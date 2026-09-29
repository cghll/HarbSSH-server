package com.gduf.domain.agent.service.armory.matter.tools;

import com.gduf.domain.agent.model.valobj.dynamic.AgentExecutionContext;
import com.gduf.domain.agent.model.valobj.dynamic.DynamicTask;
import com.gduf.domain.agent.model.valobj.dynamic.TaskStatus;
import com.gduf.domain.agent.service.armory.catalog.AgentCatalog;
import com.gduf.domain.agent.service.armory.matter.session.factory.CustomRunnerFactory;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 子 Agent 派发服务 - 动态编排体系中实际执行单个任务的 Spring 服务。
 * <p>
 * 与 {@link SubAgentDispatchTool}（包装固定子 Agent 给 LLM 调用）不同，
 * 本服务面向运行期规划出的任务列表：从 AgentCatalog 查找任务指定的子 Agent，
 * 透传父会话绑定的 SSH 终端会话（ThreadLocal），带超时执行并回写任务状态。
 * 编排器（DynamicAgentOrchestrator）通过本服务完成每个 DynamicTask 的执行。
 */
@Slf4j
@Service
public class SubAgentDispatchService {

    private final AgentCatalog agentCatalog;
    private final CustomRunnerFactory runnerFactory;
    private final Executor executor;

    public SubAgentDispatchService(AgentCatalog agentCatalog,
                                   CustomRunnerFactory runnerFactory,
                                   Executor executor) {
        this.agentCatalog = agentCatalog;
        this.runnerFactory = runnerFactory;
        this.executor = executor;
    }

    /**
     * 重试退避间隔上限（秒），指数退避 2^n 到此封顶
     */
    private static final long MAX_BACKOFF_SECONDS = 30;

    /**
     * 并发派发一批任务（无依赖编排的简单批量场景）。
     * 每个任务提交到线程池执行，全部完成后汇总：tasks（含状态/结果）与 allSucceeded。
     */
    public Map<String, Object> dispatch(AgentExecutionContext context, List<DynamicTask> tasks) {
        Map<String, Object> response = new LinkedHashMap<>();
        //Semaphore（信号量）:就是控制同时能有多少个线程干活的计数器。
        Semaphore concurrency = new Semaphore(Math.max(1, tasks.size()));

        List<CompletableFuture<Void>> futures = tasks.stream()
                .map(task -> CompletableFuture.runAsync(() -> {
                    try {
                        concurrency.acquire();  // 拿通行证
                        execute(context, task);
                    } catch (Exception exception) {
                        task.setStatus(TaskStatus.FAILED);      // 失败就标记
                        task.setError(exception.getMessage());
                    } finally {
                        concurrency.release();      // 无论如何都归还通行证
                    }
                }, executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        response.put("tasks", tasks);
        response.put("allSucceeded", tasks.stream().allMatch(task -> task.getStatus() == TaskStatus.COMPLETED));
        return response;
    }

    /**
     * 执行单个任务并原地更新任务状态（含失败重试）：
     * <ol>
     *   <li>置为 RUNNING，从 AgentCatalog 查找子 Agent（先按父 Agent 精确匹配，再全局兜底），找不到直接置 FAILED（确定性错误，不重试）</li>
     *   <li>按 maxRetries（默认 0）循环尝试：每次以独立会话运行子 Agent，按任务超时时间（默认 120 秒）阻塞等待</li>
     *   <li>成功则回写 result 并置 COMPLETED；执行期异常按 2^n 秒指数退避（上限 30 秒）后重试，重试耗尽置 FAILED</li>
     *   <li>回写 attempts（实际尝试次数），finally 中清理 ThreadLocal，避免线程池复用导致的会话串扰</li>
     * </ol>
     */
    public void execute(AgentExecutionContext context, DynamicTask task) {
        task.setStatus(TaskStatus.RUNNING);
        String invocationId = UUID.randomUUID().toString();
        BaseAgent agent = agentCatalog.find(context.getAgentId(), task.getAgentName())
                .or(() -> agentCatalog.findByName(task.getAgentName()))
                .orElse(null);

        if (agent == null) {
            task.setStatus(TaskStatus.FAILED);
            task.setError("agent not found: " + task.getAgentName());
            return;
        }

        // 最大尝试次数 = 首次执行 + 重试次数；maxRetries 做下限保护，避免 LLM 传负数
        int maxAttempts = 1 + Math.max(0, task.getMaxRetries() == null ? 0 : task.getMaxRetries());
        try {
            if (context.getTerminalSessionId() != null) {
                SshExecuteAdkTool.setCurrentTerminalSession(context.getTerminalSessionId());
            }
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                task.setAttempts(attempt);
                try {
                    Runner runner = runnerFactory.create(agent, agent.name(), List.of());
                    Content content = Content.fromParts(Part.fromText(task.getRequest()));
                    RunConfig runConfig = RunConfig.builder().autoCreateSession(true).build();

                    List<Event> events = runner.runAsync("subagent-user", "subagent-" + invocationId + "-" + attempt, content, runConfig)
                            .timeout(Optional.ofNullable(task.getTimeoutSeconds()).orElse(120), TimeUnit.SECONDS)
                            .toList()
                            .blockingGet();

                    task.setResult(toResult(events));
                    task.setStatus(TaskStatus.COMPLETED);
                    return;
                } catch (Exception e) {
                    if (attempt < maxAttempts) {
                        long backoff = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(attempt, 5));
                        log.warn("子Agent执行失败，{}秒后重试 | agent={} | task={} | attempt={}/{} | error={}",
                                backoff, task.getAgentName(), task.getTaskId(), attempt, maxAttempts, e.getMessage());
                        sleepQuietly(backoff);
                    } else {
                        task.setStatus(TaskStatus.FAILED);
                        task.setError("attempts=" + attempt + " | " + e.getMessage());
                        log.error("子Agent执行失败（重试耗尽） | agent={} | task={} | attempts={}", task.getAgentName(), task.getTaskId(), attempt, e);
                    }
                }
            }
        } finally {
            SshExecuteAdkTool.clearCurrentTerminalSession();
        }

    }


    /**
     * 退避等待：被中断时恢复中断标记并直接返回（任务让位给外层超时/取消语义）
     */
    private void sleepQuietly(long seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 拼接事件中全部文本片段作为任务执行结果（取空时返回空串）
     */
    private String toResult(List<Event> events) {
        return events.isEmpty() ? "" : events.get(events.size() - 1).content()
                .flatMap(Content::parts)
                .flatMap(parts -> parts.stream()
                        .map(part -> part.text().orElse(""))
                        .reduce((left, right) -> left + right))
                .orElse("");
    }
}
