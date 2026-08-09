package com.gduf.domain.agent.service.armory.matter.mcp.client.factpry;

import com.gduf.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.gduf.domain.agent.service.armory.matter.mcp.client.ToolMcpCreateService;
import com.gduf.domain.agent.service.armory.matter.mcp.client.impl.LocalToolMcpCreateService;
import com.gduf.domain.agent.service.armory.matter.mcp.client.impl.SSEToolMcpCreateService;
import com.gduf.domain.agent.service.armory.matter.mcp.client.impl.StdioToolMcpCreateService;
import com.gduf.types.enums.ResponseCode;
import com.gduf.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DefaultMcpClientFactory {
    @Resource
    private LocalToolMcpCreateService localToolMcpCreateService;
    @Resource
    private SSEToolMcpCreateService sseToolMcpCreateService;
    @Resource
    private StdioToolMcpCreateService stdioToolMcpCreateService;
    public ToolMcpCreateService getMcpCreateService(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp){
        if(toolMcp.getLocal()!=null) return localToolMcpCreateService;
        if(toolMcp.getSse()!=null) return sseToolMcpCreateService;
        if(toolMcp.getStdio()!=null) return stdioToolMcpCreateService;
        throw new AppException(ResponseCode.NOT_FOUND_METHOD.getCode(),ResponseCode.NOT_FOUND_METHOD.getInfo());
    }
}
