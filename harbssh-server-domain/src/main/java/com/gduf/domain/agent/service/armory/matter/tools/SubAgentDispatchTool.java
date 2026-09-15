package com.gduf.domain.agent.service.armory.matter.tools;

import com.gduf.domain.agent.service.armory.matter.session.factory.CustomRunnerFactory;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 子 Agent 派发工具（单个） - 把一个子 Agent 包装成 ADK FunctionTool 供父 Agent 调用。
 * <p>
 * 配置了 subAgents 的父 Agent 装配时，每个子 Agent 都会被包装为本工具
 * （工具名/描述即子 Agent 的 name/description，LLM 通过函数调用触发派发）。
 * 派发过程：为子 Agent 创建独立 Runner 与会话，同步收集全部事件，
 * 取最后一个事件的文本作为执行结果返回给父 Agent。
 * <p>
 * 注：本工具在装配阶段创建（非 Spring 管理），需传入 runnerFactory 手工构造 Runner。
 */
@Slf4j
public class SubAgentDispatchTool extends BaseTool {

    /** 被包装、派发的子 Agent */
    private final BaseAgent subAgent;

    /** Runner 工厂，为子 Agent 构建独立执行器,因为之前调整adk内部上下文之后需要用这个配置 */
    private final CustomRunnerFactory runnerFactory;

    public SubAgentDispatchTool(String name, String description, BaseAgent subAgent, CustomRunnerFactory runnerFactory) {
        super(name, description);
        this.subAgent = subAgent;
        this.runnerFactory = runnerFactory;
    }

    /**
     * 声明工具的函数签名：入参仅一个必填字符串 request（下发给子 Agent 的任务指令）。
     */
    @Override
    public Optional<FunctionDeclaration> declaration() {
        return Optional.of(FunctionDeclaration.builder()
                .name(name())       // 工具名 = 子 Agent 的 name
                .description(description())     // 工具描述 = 子 Agent 的 description
                .parameters(Schema.builder()
                        .type("OBJECT")         // 入参是一个 JSON 对象
                        .properties(ImmutableMap.of(
                                "request", Schema.builder().type("STRING").build()))      // 该对象只有一个字段 request，字符串类型
                        .required(ImmutableList.of("request"))      // request 必填
                        .build())
                .build());
    }

    /**
     * 执行派发：以独立会话运行子 Agent，阻塞收集全部事件后汇总结果。
     * 执行异常时返回 {success:false, error:...}，由父 Agent 感知并决定下一步。
     * 模型决定调用后，真正用 args.get("request") 拿指令，建独立 Runner/会话跑子 Agent，阻塞收完所有事件，取最后一条的文本当结果返回
     */
    @Override
    public Single<Map<String, Object>> runAsync(Map<String, Object> args, ToolContext toolContext) {
        String request = String.valueOf(args.getOrDefault("request", ""));
        String invocationId = UUID.randomUUID().toString();

        log.info("子Agent派发开始 | subAgent:{} | request:{} | invocationId:{}",
                subAgent.name(), request, invocationId);

        Runner runner = runnerFactory.create(subAgent, subAgent.name(), List.of());
        Content content = Content.fromParts(Part.fromText(request));

        RunConfig runConfig = RunConfig.builder()
                .autoCreateSession(true)
                .build();

        return runner.runAsync("subagent-user", "subagent-" + invocationId, content, runConfig)
                .toList()
                .map(events -> toResult(events, invocationId))
                .onErrorReturn(error -> {
                    log.error("子Agent派发异常 | subAgent:{} | invocationId:{}",
                            subAgent.name(), invocationId, error);
                    return ImmutableMap.of(
                            "success", false,
                            "error", String.valueOf(error.getMessage()));
                });
    }

    /** 取最后一个事件的文本内容作为子 Agent 的最终回复 */
    private Map<String, Object> toResult(List<Event> events, String invocationId) {
        String result = events.isEmpty() ? "" : events.get(events.size() - 1)
                .content()
                .map(Content::text)
                .orElse("");

        log.info("子Agent派发完成 | subAgent:{} | resultLength:{} | invocationId:{}",
                subAgent.name(), result.length(), invocationId);

        return ImmutableMap.of(
                "success", true,
                "result", result);
    }
}
