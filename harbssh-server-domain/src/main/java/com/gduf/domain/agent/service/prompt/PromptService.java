package com.gduf.domain.agent.service.prompt;

import com.gduf.domain.agent.model.prompt.MilestoneVO;
import com.gduf.domain.agent.model.prompt.PromptContextVO;
import com.gduf.domain.agent.service.IPromptService;
import com.gduf.domain.agent.service.prompt.dynamic.DynamicPromptBuilder;
import com.gduf.domain.agent.service.prompt.dynamic.MilestoneTracker;
import com.gduf.domain.ssh.service.ISshTerminalService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * 提示词领域服务接口
 * <p>
 * 封装动态 Prompt 构建、里程碑追踪、环境信息采集等能力，
 * 使 case 层仅依赖此接口，不直接接触 DynamicPromptBuilder / MilestoneTracker 等内部组件。
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

    /** SSH 终端服务——提供实时命令执行通道，用于采集远程环境信息（uname/whoami/pwd） */
    @Resource
    private ISshTerminalService sshTerminalService;

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
    public String buildEnrichedMessage(String userMessage, String sessionId, String terminalSessionId, List<String> recentCommands) {
        // 1. 从 SSH 终端采集环境信息
        PromptContextVO promptContextVO = buildPromptContext(sessionId, terminalSessionId, recentCommands);
        // 2. 生成消息前缀
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

    /**
     * 构建动态 Prompt 上下文值对象。
     * <p>
     * 通过 SSH 终端实时采集三类环境信息：
     * <ul>
     *   <li><code>uname -srm</code> → osInfo（操作系统架构）</li>
     *   <li><code>whoami</code> → currentUser（当前登录用户）</li>
     *   <li><code>pwd</code> → currentDirectory（当前工作目录）</li>
     * </ul>
     * 工程细节：
     * <ul>
     *   <li>每条采集命令独立 try-catch——环境采集是"锦上添花"非"必需成功"，失败留空，Builder 自动跳过</li>
     *   <li>复用已有终端通道（ISshTerminalService），零额外成本</li>
     * </ul>
     * 最后从 {@link MilestoneTracker} 取最近 10 条里程碑，与 recentCommands 一起组装成 {@link PromptContextVO}。
     *         对话会话 ID
     * @param terminalSessionId SSH 终端会话 ID，为 null 或空时跳过环境采集
     * @param recentCommands    最近执行的命令列表
     * @return 组装完成的动态上下文值对象
     */
    private PromptContextVO buildPromptContext(String sessionId, String terminalSessionId, List<String> recentCommands) {
        String osInfo = "";
        String currentUser = "";
        String currentDirectory = "";

        if (terminalSessionId != null && !terminalSessionId.isEmpty()) {
            try {
                String raw = sshTerminalService.executeCommand(terminalSessionId, "uname -srm");
                osInfo = raw != null ? raw.trim() : "";
            } catch (Exception e) {
                log.debug("获取 OS 信息失败: {}", e.getMessage());
            }

            try {
                String raw = sshTerminalService.executeCommand(terminalSessionId, "whoami");
                currentUser = raw != null ? raw.trim() : "";
            } catch (Exception e) {
                log.debug("获取用户信息失败: {}", e.getMessage());
            }

            try {
                String raw = sshTerminalService.executeCommand(terminalSessionId, "pwd");
                currentDirectory = raw != null ? raw.trim() : "";
            } catch (Exception e) {
                log.debug("获取工作目录失败: {}", e.getMessage());
            }
        }

        List<MilestoneVO> milestoneVOS = milestoneTracker.getRecent(sessionId, 10);

        return PromptContextVO.builder()
                .osInfo(osInfo)
                .currentUser(currentUser)
                .currentDirectory(currentDirectory)
                .recentCommands(recentCommands)
                .milestoneVOS(milestoneVOS)
                .build();
    }
}
