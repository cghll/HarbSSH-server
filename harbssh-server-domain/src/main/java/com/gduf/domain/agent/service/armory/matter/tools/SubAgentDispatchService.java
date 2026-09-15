package com.gduf.domain.agent.service.armory.matter.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
}
