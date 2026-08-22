package com.gduf.cases.react.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.gduf.api.dto.ChatRequestDTO;
import com.gduf.api.dto.ReActResultDTO;
import com.gduf.cases.react.AbstractAIAgentReActSupport;
import com.gduf.cases.react.factory.DefaultReActFactory;
import com.gduf.domain.agent.service.IChatContextService;
import com.gduf.domain.agent.service.IPromptService;
import com.gduf.domain.agent.service.armory.matter.tools.SshExecuteAdkTool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct 工具执行节点
 *
 * <p>职责：
 * 1. 从上下文中获取 AI 返回的工具调用列表（由 AiCallNode 设置）
 * 2. 检查工具是否已被 ADK 自动执行（FunctionResponse 已存在）
 * 3. 整理自动执行的工具事件并记录补偿日志
 * 4. 路由：到 LoopDecisionNode 检查终止条件
 *
 * <p>注意：在当前 ADK 自动执行模式下，此节点不承担真实的循环发起职责。
 */
@Slf4j
@Component("reactToolCallNode")
public class ToolCallNode extends AbstractAIAgentReActSupport {

    @Resource
    private SshExecuteAdkTool sshExecuteAdkTool;

    @Resource
    private IPromptService promptService;

    @Resource
    private IChatContextService chatContextService;

    @Override
    protected ReActResultDTO doApply(ChatRequestDTO requestParameter, DefaultReActFactory.DynamicContext dynamicContext) throws Exception {
        List<Map<String, Object>> toolCalls = dynamicContext.getCurrentToolCalls();
        List<Map<String, Object>> toolResults = dynamicContext.getCurrentToolResults();

        if (toolCalls == null || toolCalls.isEmpty()) {
            log.info("ReAct ToolCallNode - 无工具调用，跳过");
            return router(requestParameter, dynamicContext);
        }

        log.info("ReAct ToolCallNode - 处理 {} 个工具调用，已有 {} 个结果",
                toolCalls.size(), toolResults != null ? toolResults.size() : 0);

        ResponseBodyEmitter emitter = dynamicContext.getEmitter();

        // 检查是否已有 ADK 自动执行的结果（FunctionResponse 已返回）
        boolean adkAutoExecuted = toolResults != null && !toolResults.isEmpty();

        // 这种场景就是用户的配置的 LLM 质量不高的时候，一种鲁棒设计，尽量的增强可靠性
        if (adkAutoExecuted) {
            // ─── ADK 自动执行模式 ───
            log.info("工具已被 ADK 自动执行，记录执行痕迹");
            handleAdkToolResults(dynamicContext, toolCalls, toolResults);
        } else {
            // ─── 手动执行模式 ───
            log.info("工具未执行，手动执行");
            handleManualToolExecution(dynamicContext, toolCalls, emitter);
        }

        // 路由
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ChatRequestDTO, DefaultReActFactory.DynamicContext, ReActResultDTO> get(ChatRequestDTO chatRequestDTO,
                                                       DefaultReActFactory.DynamicContext dynamicContext) throws Exception {
        // 在 ADK 自动执行主循环架构下，ToolCallNode 只是观测和整理节点，直接路由到 LoopDecisionNode 判断收尾条件。
        log.info("工具观测节点处理完成，路由到 LoopDecisionNode 检查终止条件");
        return getBean("reactLoopDecisionNode");
    }

    // ═══════════════════════════════════════════════════════════════
    //  ADK 自动执行模式
    // ═══════════════════════════════════════════════════════════════
    private void handleAdkToolResults(DefaultReActFactory.DynamicContext dynamicContext,
                                      List<Map<String, Object>> toolCalls,
                                      List<Map<String, Object>> toolResults) {

        // ADK runner 内部已管理对话历史（自动执行工具 + 追加 FunctionResponse）
        // 这里只做日志记录，不重复追加消息到 history
        Map<String, Map<String, Object>> resultMap = new HashMap<>();
        for (Map<String, Object> result : toolResults) {
            String id = (String) result.get("id");
            if (id != null) {
                resultMap.put(id, result);
            }
        }

        for (Map<String, Object> toolCall : toolCalls) {
            String toolCallId = (String) toolCall.get("id");
            String toolName = (String) toolCall.get("name");

            Map<String, Object> matchedResult = resultMap.get(toolCallId);
            if (matchedResult != null) {
                String content = (String) matchedResult.get("content");
                log.info("ADK 工具结果记录完毕: id={}, name={}, result_length={}",
                        toolCallId, toolName, content != null ? content.length() : 0);

                // 补全 ADK 自动执行模式下缺失的 messageHistory 写入
                dynamicContext.appendToolMessage(toolCallId, content);
            } else {
                log.warn("未找到工具结果记录: id={}, name={}", toolCallId, toolName);
            }
        }

        // 清除本轮工具调用标记，避免重复路由
        // 注意：toolResults 保留，供 UserFeedbackNode 构建最终结果
        dynamicContext.getCurrentToolCalls().clear();
    }

    // ═══════════════════════════════════════════════════════════════
    //  手动执行模式（未来扩展）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 手动执行工具调用
     * <p>当 ADK 未自动执行工具时，由 ToolCallNode 直接执行
     * <p>适用于：自定义工具、MCP 工具、需要预处理/后处理的场景
     */
    private void handleManualToolExecution(DefaultReActFactory.DynamicContext dynamicContext,
                                           List<Map<String, Object>> toolCalls,
                                           ResponseBodyEmitter emitter) throws Exception {

        for (Map<String, Object> toolCall : toolCalls) {
            String toolCallId = (String) toolCall.get("id");
            String toolName = (String) toolCall.get("name");
            String argsStr = (String) toolCall.get("args");

            if (toolCallId == null || toolName == null) {
                log.warn("工具调用信息不完整: {}", toolCall);
                continue;
            }

            // 发送 tool_call executing 事件
            sendToolCallEvent(emitter, toolCallId, toolName, "executing");

            // 执行工具
            String resultContent;
            String status = "success";
            try {
                resultContent = executeTool(toolName, argsStr);
                log.info("工具执行成功: name={}, result_length={}", toolName, resultContent.length());
            } catch (Exception e) {
                log.error("工具执行失败: name={}", toolName, e);
                resultContent = "Error executing tool '" + toolName + "': " + e.getMessage();
                status = "error";
            }

            // 截断过长结果
            resultContent = truncateToolResponse(resultContent, 4000);

            // 存储工具结果到上下文
            Map<String, Object> toolResult = new HashMap<>();
            toolResult.put("id", toolCallId);
            toolResult.put("name", toolName);
            toolResult.put("content", resultContent);
            toolResult.put("status", status);
            dynamicContext.getCurrentToolResults().add(toolResult);

            // 追加 tool 消息到消息历史（供下一轮 AI 调用使用）
            dynamicContext.appendToolMessage(toolCallId, resultContent);

            // 记录里程碑和工具执行摘要（供下一轮 Prompt 注入）
            promptService.detectAndRecordMilestone(dynamicContext.getSessionId(),"tool",resultContent);

            chatContextService.pushToolResult(dynamicContext.getSessionId(), toolName, resultContent);

            // 发送 tool_result SSE 事件
            sendToolResultEvent(emitter, toolCallId, resultContent, status);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  工具执行
    // ═══════════════════════════════════════════════════════════════

    private String executeTool(String toolName, String args) throws Exception {
        if ("executeCommand".equals(toolName)) {
            String command = resolveCommandArgument(args);
            Map<String, Object> result = sshExecuteAdkTool.executeCommand(command);
            return formatToolExecutionResult(result);
        }
        throw new UnsupportedOperationException("Unsupported tool: " + toolName);
    }

    private String resolveCommandArgument(String args) {
        if (args == null || args.isBlank()) {
            return "";
        }

        String trimmedArgs = args.trim();
        if (!trimmedArgs.startsWith("{")) {
            return trimmedArgs;
        }

        try {
            JsonNode root = objectMapper.readTree(trimmedArgs);
            JsonNode commandNode = root.get("command");
            if (commandNode != null && !commandNode.isNull()) {
                return commandNode.asText("");
            }
        } catch (Exception e) {
            log.warn("解析工具参数失败，按原始字符串执行: args={}", trimmedArgs, e);
        }

        return trimmedArgs;
    }

    private String formatToolExecutionResult(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return "";
        }

        String output = valueAsString(result.get("output"));
        String suggestion = valueAsString(result.get("suggestion"));
        boolean success = Boolean.TRUE.equals(result.get("success"));

        if (success || suggestion.isBlank()) {
            return output.isBlank() ? valueAsString(result) : output;
        }

        if (output.isBlank()) {
            return suggestion;
        }

        return output + "\nSuggestion: " + suggestion;
    }

    private String valueAsString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String truncateToolResponse(String response, int maxLength) {
        if (response == null || response.length() <= maxLength) {
            return response;
        }
        return response.substring(0, maxLength) + "\n...[Truncated]";
    }
}
