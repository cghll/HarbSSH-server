package com.gduf.cases.react.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.gduf.api.dto.ChatRequestDTO;
import com.gduf.api.dto.ReActResultDTO;
import com.gduf.cases.react.AbstractAIAgentReActSupport;
import com.gduf.cases.react.factory.DefaultReActFactory;
import com.gduf.domain.agent.model.entity.ChatMessageEntity;
import com.gduf.domain.agent.service.ILongTermMemoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ReAct Root Node（根节点）
 *
 * <p>职责：
 * 1. 从 ChatRequestDTO 提取会话参数
 * 2. 初始化 DynamicContext
 * 3. 绑定终端会话 ID（ThreadLocal 和 Session 映射双重绑定）
 * 4. 路由到 AiCallNode
 *
 * <p>节点链：
 * RootNode → AiCallNode → ToolCallNode → LoopDecisionNode → UserFeedbackNode
 */
@Slf4j
@Component("reactRootNode")
public class RootNode extends AbstractAIAgentReActSupport {

    private static final int DEFAULT_MAX_STEPS = 50;
    private static final int DEFAULT_MAX_TOOL_CALLS = 200;
    private static final int DEFAULT_MAX_TOOL_CALLS_PER_ROUND = 10;

    @Resource
    private ILongTermMemoryService longTermMemoryService;

    @Override
    protected ReActResultDTO doApply(ChatRequestDTO requestParameter, DefaultReActFactory.DynamicContext dynamicContext) throws Exception {
        log.info("ReAct RootNode - 初始化上下文");

        //1.提取会话参数
        String sessionId = requestParameter.getSessionId();
        String userId = requestParameter.getUserId();
        String agentId = requestParameter.getAgentId();
        String terminalSessionId = requestParameter.getTerminalSessionId();
        String message = requestParameter.getMessage();

        //2.绑定终端会话（ThreadLocal + 映射绑定，支持异步和跨请求继承）
        if(terminalSessionId!=null && !terminalSessionId.isEmpty()){
            bindTerminalSession(sessionId, terminalSessionId);
        }else{
            //尝试从会话绑定中获取
            String boundTerminal = getTerminalSession(sessionId);
            if(boundTerminal!=null){
                setCurrentTerminalSession(boundTerminal);
                dynamicContext.setTerminalSessionId(boundTerminal); // 补齐 dynamicContext 中的值
            }
        }

        // 3. 初始化上下文
        dynamicContext.setSessionId(sessionId);
        dynamicContext.setUserId(userId);
        dynamicContext.setAgentId(agentId);

        if (dynamicContext.getTerminalSessionId() == null) {
            dynamicContext.setTerminalSessionId(terminalSessionId);
        }

        // 记录首轮最干净的原始任务，防止在长对话或前缀注入后被污染
        dynamicContext.setOriginalUserTask(message);
        // 冷启动恢复（新增）：从 DB 加载最近 50 条历史消息，恢复对话上下文。
        // 这样即使服务重启，用户之前排查的上下文也能接上，而不是从空开始。
        List<Map<String, Object>> history = new ArrayList<>();
        // 冷启动恢复（新增）：委托领域服务从 DB 加载最近 50 条历史消息，恢复对话上下文，
        // case 层不再直接调用仓储层。
        List<ChatMessageEntity> recentMessages = longTermMemoryService.getRecentMessages(sessionId, 50);
        for (ChatMessageEntity msg : recentMessages) {
            Map<String, Object> map = new HashMap<>();
            map.put("role", msg.getRole());
            map.put("content", msg.getContent() != null ? msg.getContent() : "");
            // tool 消息需要补全 tool_call_id 和 name，供 ADK 框架正确关联
            if ("tool".equals(msg.getRole()) && msg.getToolCallId() != null) {
                map.put("tool_call_id", msg.getToolCallId());
                map.put("name", msg.getToolName());
            }
            history.add(map);
        }
        dynamicContext.setMessageHistory(history);
        dynamicContext.setCurrentToolCalls(new ArrayList<>());
        dynamicContext.setCurrentToolResults(new ArrayList<>());
        dynamicContext.setCurrentStep(new AtomicInteger(0));
        dynamicContext.setMaxSteps(DEFAULT_MAX_STEPS);
        dynamicContext.setMaxToolCalls(DEFAULT_MAX_TOOL_CALLS);
        dynamicContext.setMaxToolCallsPerRound(DEFAULT_MAX_TOOL_CALLS_PER_ROUND);

        // 4. 初始化结果 DTO
        ReActResultDTO result = ReActResultDTO.builder()
                .totalSteps(0)
                .totalToolCalls(0)
                .maxStepsReached(false)
                .userStopped(false)
                .idleTimeout(false)
                .build();
        dynamicContext.setResult(result);

        // 5. 追加用户消息到历史
        dynamicContext.appendUserMessage(message);

        log.info("ReAct RootNode - 初始化完成 sessionId={}, userId={}, agentId={}, terminalSessionId={}",
                sessionId, userId, agentId, dynamicContext.getTerminalSessionId());

        // 6. 路由到 AI 调用节点
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ChatRequestDTO, DefaultReActFactory.DynamicContext, ReActResultDTO>
    get(ChatRequestDTO chatRequestDTO, DefaultReActFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("reactAiCallNode");
    }
}
