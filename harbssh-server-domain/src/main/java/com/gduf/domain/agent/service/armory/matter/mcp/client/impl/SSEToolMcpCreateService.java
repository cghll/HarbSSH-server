package com.gduf.domain.agent.service.armory.matter.mcp.client.impl;

import com.gduf.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.gduf.domain.agent.service.armory.matter.mcp.client.ToolMcpCreateService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;

@Slf4j
@Service
public class SSEToolMcpCreateService implements ToolMcpCreateService {
    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception {
        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.SSEServerParameters sseConfig = toolMcp.getSse();
        String originalBaseUri = sseConfig.getBaseUri();
        String baseUri=originalBaseUri;
        String sseEndpoint = sseConfig.getSseEndpoint();
        if(StringUtils.isBlank(sseEndpoint)){
            URL url = new URL(originalBaseUri);
            //获取协议
            String protocol = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();

            //做对应截取
            String baseUrl=port==-1?protocol+"://"+host:protocol+"://"+host+":"+port;
            int index = originalBaseUri.indexOf(baseUri);
            if(index != -1){
                sseEndpoint = originalBaseUri.substring(index+baseUri.length());
            }
            baseUri = baseUrl;
        }
        //这里存纯粹是做以防万一的兜底
        sseEndpoint=StringUtils.isBlank(sseEndpoint)?"/sse":sseEndpoint;

        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport
                .builder(baseUri)
                .sseEndpoint(sseEndpoint)
                .build();
        McpSyncClient mcpSyncClient = McpClient
                .sync(sseClientTransport)
                .requestTimeout(Duration.ofMinutes(sseConfig.getRequestTimeout())).build();
        McpSchema.InitializeResult initialize = mcpSyncClient.initialize();
        log.info("Tool SSE MCP Initialized {}", initialize);

        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpSyncClient).build()
                .getToolCallbacks();
    }
}
