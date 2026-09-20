package com.gduf.domain.agent.service.armory.matter.tools;

import com.gduf.domain.agent.model.valobj.dynamic.DynamicTaskPlan;
import org.springframework.stereotype.Service;

/**
 * 任务计划校验器 - 在派发执行前对 {@link DynamicTaskPlan} 做结构与安全校验。
 * <p>
 * 整体定位：主 Agent（LLM）会动态规划出一个任务计划（包含多个子任务及其依赖关系），
 * 交给编排器执行。但 LLM 输出是<b>不可信的</b>——可能为空、任务数量超限、ID 重复、
 * 引用不存在的依赖、甚至出现循环依赖。本类是 LLM 动态规划与确定性执行器之间的
 * 一道"防呆 + 防失控"防火墙：把脏数据拦截掉，防止执行器卡死或失控。
 * <p>
 * 校验规则：
 * <ol>
 *   <li>计划非空，任务数量不超过上限（防止 LLM 规划出失控规模的任务）</li>
 *   <li>taskId 不重复（重复 ID 会导致执行器任务分派错乱、依赖指向歧义）</li>
 *   <li>所有 dependsOn 引用的任务必须存在于计划中（悬空依赖会让编排器永远等不到任务完成）</li>
 *   <li>依赖关系不能成环（三色标记 DFS 检测），否则编排器会因"无可执行任务"而卡死</li>
 * </ol>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>快速失败：结构问题（空/超限/重复）O(n) 先查，成本最高的环检测放最后</li>
 *   <li>软硬结合：硬约束（空/重复/环）直接拒绝；软约束（重试次数）自动钳制修正，
 *       避免因小事让 LLM 重试整轮规划</li>
 *   <li>任一规则不满足即抛出 IllegalArgumentException，由派发工具统一兜底
 *       返回错误信息给 LLM，让它修正计划后重试</li>
 * </ul>
 */
@Service
public class PlanValidator {
}
