package com.gduf.domain.agent.service.context;

import com.gduf.domain.agent.model.prompt.MilestoneVO;
import com.gduf.domain.agent.model.prompt.PromptContextVO;
import com.gduf.domain.agent.service.IChatContextService;
import com.gduf.domain.agent.service.context.provider.ContextProvider;
import com.gduf.domain.agent.service.context.provider.impl.ToolResultProvider;
import com.gduf.domain.agent.service.context.reducer.MessageReducer;
import com.gduf.domain.agent.service.context.reducer.impl.HybridReducer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class ChatContextService implements IChatContextService {

    private static final int DEFAULT_MAX_CONTEXT_TOKENS = 8000;

    private final List<ContextProvider> providers;


    @Resource
    private HybridReducer hybridReducer;

    @Resource
    private ToolResultProvider toolResultProvider;

    public ChatContextService(List<ContextProvider> providers) {
        this.providers = providers;
        //根据order排序
        this.providers.sort(Comparator.comparingInt(ContextProvider::getOrder));
    }

    @Override
    public PromptContextVO buildPromptContext(String sessionId, String userId, String terminalSessionId, List<Map<String, Object>>
            messageHistory) {
        Map<String, Object> finalCtx = new HashMap<>();

        for (ContextProvider provider : providers) {
            if (!provider.enabled()) continue;
            Map<String, Object> ctx = provider.provide(sessionId, userId, terminalSessionId, messageHistory);
            if (ctx != null) {
                finalCtx.putAll(ctx);
            }
        }

        return PromptContextVO.builder()
                .osInfo((String) finalCtx.get("osInfo"))
                .currentUser((String) finalCtx.get("currentUser"))
                .currentDirectory((String) finalCtx.get("currentDirectory"))
                .serverInfo((String) finalCtx.get("serverInfo"))
                .milestoneVOS((List<MilestoneVO>) finalCtx.get("milestoneVOS"))
                .toolResultSummary((String) finalCtx.get("toolResultSummary"))
                .taskDescription((String) finalCtx.get("taskDescription"))
                .build();

    }

    @Override
    public List<Map<String, Object>> trimHistory(List<Map<String, Object>> history, int tokenBudget) {
        if (history == null || history.isEmpty()) return Collections.emptyList();
        // 混合裁剪
        return hybridReducer.reduce(history, tokenBudget > 0 ? tokenBudget : DEFAULT_MAX_CONTEXT_TOKENS);
    }

    @Override
    public void pushToolResult(String sessionId, String toolName, String result) {
        toolResultProvider.pushResult(sessionId, toolName, result);
    }
}
