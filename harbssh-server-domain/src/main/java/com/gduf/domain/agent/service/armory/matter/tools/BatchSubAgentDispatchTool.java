//package com.gduf.domain.agent.service.armory.matter.tools;
//
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.google.adk.tools.BaseTool;
//import lombok.extern.slf4j.Slf4j;
//
//import java.util.List;
//
//
///**
// * 批量子 Agent 派发工具 - 主 Agent 一次函数调用即可并发派发多个子 Agent 任务。
// * <p>
// * 与 {@link DynamicPlanDispatchTool}（先由独立规划器生成计划）不同，
// * 本工具由主 Agent 在函数调用参数中直接给出任务列表（含依赖关系），
// * 经校验后交给编排器按 DAG 并发执行，用于主 Agent 自主拆解的批量诊断场景。
// * <p>
// * 注：本工具在装配阶段创建（非 Spring 管理），构造时传入允许派发的 Agent 白名单。
// */
//@Slf4j
//public class BatchSubAgentDispatchTool extends BaseTool {
//
//    /** 允许派发的子 Agent 名称白名单（所有已装配的 Agent） */
//    private final List<String> allowedAgents;
//
//    /** DAG 编排器，负责任务并发调度 */
//    private final DynamicAgentOrchestrator orchestrator;
//
//    /** 计划解析/校验器：解析函数调用参数并校验依赖合法性 */
//    private final PlanParser planParser = new PlanParser();
//    private final PlanValidator planValidator = new PlanValidator();
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    protected BatchSubAgentDispatchTool(String name, String description) {
//        super(name, description);
//    }
//}
