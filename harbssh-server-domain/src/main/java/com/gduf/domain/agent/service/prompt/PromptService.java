package com.gduf.domain.agent.service.prompt;

import com.gduf.domain.agent.model.valobj.prompt.PromptContextVO;
import com.gduf.domain.agent.service.IChatContextService;
import com.gduf.domain.agent.service.IPromptService;
import com.gduf.domain.agent.service.prompt.dynamic.DynamicPromptBuilder;
import com.gduf.domain.agent.service.prompt.dynamic.MilestoneTracker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


/**
 * 提示词服务
 * <p>
 * 组合 DynamicPromptBuilder、MilestoneTracker、IChatContextService，
 * 向 case 层提供统一的提示词领域能力。
 * <p>
 * 上下文采集（环境/任务/里程碑/工具摘要）已下沉到 IChatContextService 的
 * Provider 体系，本类只负责组装前缀并拼接到用户消息。
 */
@Slf4j
@Service
public class PromptService implements IPromptService {

    /** 动态提示词构建器——负责组装结构化消息前缀（OS/用户/目录/里程碑/最近命令） */
    @Resource
    private DynamicPromptBuilder dynamicPromptBuilder;

    /** 里程碑追踪器——检测并缓存用户纠偏、任务切换等关键事件，供动态 Prompt 引用 */
    @Resource
    private MilestoneTracker milestoneTracker;

    /** 上下文管理服务——聚合各 ContextProvider 输出，组装 PromptContextVO */
    @Resource
    private IChatContextService chatContextService;

    /**
     * 检测并记录里程碑事件（用户纠偏、任务切换、错误等）。
     * <p>
     * 委托 {@link MilestoneTracker#detectAndRecord} 完成实际识别和缓存。
     *
     * @param sessionId 对话会话 ID
     * @param role      消息角色："user" 或 "tool"
     * @param content   消息内容
     */
    @Override
    public void detectAndRecordMilestone(String sessionId, String role, String content) {
        milestoneTracker.detectAndRecord(sessionId,role,content);
    }

    @Override
    public String buildEnrichedMessage(String userMessage, String sessionId, String userId, String terminalSessionId, List<String> recentCommands, List<Map<String, Object>> messageHistory) {
        PromptContextVO promptContextVO = chatContextService.buildPromptContext(sessionId, userId, terminalSessionId, messageHistory);
        promptContextVO.setRecentCommands(recentCommands);

        String prefix = dynamicPromptBuilder.buildMessagePrefix(promptContextVO);
        if (prefix.isEmpty()) {
            return userMessage;
        }

        return prefix + "\n---\n" + userMessage;
    }

    @Override
    public void clearMilestones(String sessionId) {
        milestoneTracker.clear(sessionId);
    }

}
