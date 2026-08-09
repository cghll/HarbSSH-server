package com.gduf.domain.agent.service.armory.matter.mcp.client;

import com.gduf.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.springframework.ai.tool.ToolCallback;

import java.net.MalformedURLException;

/**
 * 工具 mcp接口
 */
public interface ToolMcpCreateService {
    ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception;
}
