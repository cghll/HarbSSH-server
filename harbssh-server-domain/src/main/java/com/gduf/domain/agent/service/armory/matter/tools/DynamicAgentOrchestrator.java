//package com.gduf.domain.agent.service.armory.matter.tools;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.concurrent.Executor;
//
///**
// * 动态 Agent 编排器 - 按任务依赖关系（DAG）调度执行一批子 Agent 任务。
// * <p>
// * 调度策略：
// * <ol>
// *   <li>每轮从计划中筛选出"所有依赖任务均已 COMPLETED"的 PENDING 任务</li>
// *   <li>把这批就绪任务并发提交线程池，通过 Semaphore 限制并发上限（maxConcurrency）</li>
// *   <li>等待本轮全部结束后进入下一轮，直到没有 PENDING 任务（或死锁则提前退出）</li>
// * </ol>
// * 失败处理策略：
// * <ul>
// *   <li>单任务执行异常只置该任务为 FAILED（执行服务内部已按 maxRetries 重试），不中断整体</li>
// *   <li>依赖了 FAILED/SKIPPED 任务的下游 PENDING 任务被显式置为 SKIPPED，并沿依赖链级联传播</li>
// *   <li>计划开启 failFast 时，首个任务失败后不再调度新任务（进行中的跑完），剩余 PENDING 全部置 SKIPPED</li>
// * </ul>
// * 全部执行完成后返回汇总结果（计划、各任务状态、是否全部成功）。
// */
//@Slf4j
//@Service
//public class DynamicAgentOrchestrator {
//
//    private final SubAgentDispatchService dispatchService;
//    private final Executor executor;
//
//    public DynamicAgentOrchestrator(SubAgentDispatchService dispatchService, Executor executor) {
//        this.dispatchService = dispatchService;
//        this.executor = executor;
//    }
//}
