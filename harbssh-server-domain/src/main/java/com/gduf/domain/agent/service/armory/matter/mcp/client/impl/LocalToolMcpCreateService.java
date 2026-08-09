package com.gduf.domain.agent.service.armory.matter.mcp.client.impl;

import com.gduf.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.gduf.domain.agent.service.armory.matter.mcp.client.ToolMcpCreateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LocalToolMcpCreateService implements ToolMcpCreateService {

    @Resource
    private ApplicationContext applicationContext;
    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) {
        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.LocalParameters local = toolMcp.getLocal();
        String name = local.getName();
        ToolCallbackProvider localToolCallbackProvider = (ToolCallbackProvider) applicationContext.getBean(name);
        log.info("Tool Local MCP Initialized {}", name);
        return localToolCallbackProvider.getToolCallbacks();
    }
}
