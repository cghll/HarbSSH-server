package com.gduf.domain.agent.service.context.provider.impl;

import com.gduf.domain.agent.service.context.provider.ContextProvider;
import com.gduf.domain.ssh.service.terminal.SshTerminalService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * 终端状态上下文提供者（order=10，最先执行）
 * <p>
 * 功能：通过 SSH 终端实时采集远程服务器的环境信息（OS/用户/工作目录/运行时长），
 * 让模型"知道自己在哪台机器上操作"。该逻辑从 PromptService 下沉至此。
 * <p>
 * 运行过程：
 * <pre>
 *   provide(sessionId, userId, terminalSessionId, history)
 *        |
 *        | terminalSessionId 为空 ? --> 返回空 Map（无终端则不采集）
 *        v
 *   safeExec(terminalSessionId, cmd)  逐条执行采集命令
 *        |
 *        +-- "uname -srm"  --> osInfo          （操作系统/架构）
 *        +-- "whoami"      --> currentUser     （当前登录用户）
 *        +-- "pwd"         --> currentDirectory（当前工作目录）
 *        +-- "uptime"      --> uptime          （运行时长）
 *        |
 *        v
 *   Map{osInfo, currentUser, currentDirectory, uptime}
 *        |
 *        v
 *   ChatContextService 合并 --> PromptContextVO --> 消息前缀 [系统环境]
 * </pre>
 * 容错设计：每条命令独立 try-catch（safeExec），单条失败仅该字段留空，
 * 环境采集是"锦上添花"，绝不阻断主流程。
 */
@Slf4j
@Component
public class TerminalStateProvider implements ContextProvider {

    @Resource
    private SshTerminalService sshTerminalService;

    @Override
    public String getName() {
        return "terminal-state";
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Map<String, Object> provide(String sessionId, String userId, String terminalSessionId, List<Map<String, Object>> messageHistory) {
        Map<String, Object> result = new HashMap<>();
        if (terminalSessionId == null || terminalSessionId.isEmpty()) {
            return result;  // 无终端会话，不采集
        }
        // "uname -srm"  --> osInfo          （操作系统/架构）
        // "whoami"      --> currentUser     （当前登录用户）
        // "pwd"         --> currentDirectory（当前工作目录）
        // "uptime"      --> uptime          （运行时长）
        result.put("osInfo", safeExec(terminalSessionId, "uname -srm"));
        result.put("currentUser", safeExec(terminalSessionId, "whoami"));
        result.put("currentDirectory", safeExec(terminalSessionId, "pwd"));
        result.put("uptime", safeExec(terminalSessionId, "uptime -p 2>/dev/null || uptime"));
        return result;
    }

    /** 执行采集命令，失败返回空串（环境采集是锦上添花，不阻断主流程） */
    private String safeExec(String terminalSessionId, String cmd) {
        try {
            String res = sshTerminalService.executeCommand(terminalSessionId, cmd);
            return res != null ? res.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
