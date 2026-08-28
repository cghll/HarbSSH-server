package com.gduf.domain.agent.model.valobj.prompt;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PromptContextVO {
    //服务器信息
    private String serverInfo;
    //操作系统信息
    private String osInfo;
    //当前登录用户，如ubuntu，root
    private String currentUser;
    //当前工作目录
    private String currentDirectory;

    /**
     * 执行命令
     */
    private List<String> recentCommands;
    /**
     * 里程碑记录
     */
    private List<MilestoneVO> milestoneVOS;

    /**
     * 工具执行摘要
     */
    private String toolResultSummary;

    /**
     * 当前任务描述（首条用户消息）
     */
    private String taskDescription;

    /**
     * 当前意图标签（由意图识别系统经 PromptService.buildEnrichedMessage(intentLabel) 注入）。
     * <p>
     * 值为 {@code IntentTypeEnumVO.name()}（如 "DIAGNOSE"），可为 null 表示未识别。
     * 由 {@code DynamicPromptBuilder} 渲染为消息前缀 "[用户意图] xxx"，让主模型感知意图但不强制路由。
     */
    private String intentLabel;
}
