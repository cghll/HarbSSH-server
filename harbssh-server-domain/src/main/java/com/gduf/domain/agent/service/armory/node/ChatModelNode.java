package com.gduf.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.gduf.domain.agent.model.entity.ArmoryCommandEntity;
import com.gduf.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.gduf.domain.agent.model.valobj.AiAgentRegisterVO;
import com.gduf.domain.agent.service.armory.AbstractArmorySupport;
import com.gduf.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.gduf.domain.agent.service.armory.matter.mcp.client.ToolMcpCreateService;
import com.gduf.domain.agent.service.armory.matter.mcp.client.factpry.DefaultMcpClientFactory;
import com.gduf.domain.agent.service.armory.matter.skills.ToolSkillsCreateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChatModelNode extends AbstractArmorySupport {
    @Resource
    private AgentNode agentNode;
    @Resource
    private DefaultMcpClientFactory defaultMcpClientFactory;
    @Resource
    private ToolSkillsCreateService toolSkillsCreateService;
    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("AI Agent 装配操作-ChatModelNode");

        //获取上下文对象
        OpenAiApi openAiApi = dynamicContext.getOpenAiApi();

        //获取配置对象
        AiAgentConfigTableVO.Module.ChatModel chatModelConfig = requestParameter.getAiAgentConfigTableVO().getModule().getChatModel();
        List<AiAgentConfigTableVO.Module.ChatModel.ToolMcp> toolMcpList = chatModelConfig.getToolMcpList();
        List<AiAgentConfigTableVO.Module.ChatModel.ToolSkills> toolSkillsList = chatModelConfig.getToolSkillsList();

        //构建mcp服务，（工厂）
        List<ToolCallback> toolCallbackList=new ArrayList<>();
        if(toolMcpList!=null&&!toolMcpList.isEmpty()){
            for(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp:toolMcpList){
                ToolMcpCreateService toolMcpCreateService = defaultMcpClientFactory.getMcpCreateService(toolMcp);
                ToolCallback[] toolCallbacks = toolMcpCreateService.buildToolCallback(toolMcp);
                toolCallbackList.addAll(List.of(toolCallbacks));
            }
        }

        if(toolSkillsList!=null&&!toolSkillsList.isEmpty()){
            for(AiAgentConfigTableVO.Module.ChatModel.ToolSkills toolSkills:toolSkillsList){
                ToolCallback[] toolCallbacks = toolSkillsCreateService.buildToolCallback(toolSkills);
                toolCallbackList.addAll(List.of(toolCallbacks));
            }
        }

        //构建对话模型
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(chatModelConfig.getModel())
                .toolCallbacks(toolCallbackList)
                // 开启流式 usage 统计：OpenAI 协议要求 stream_options.include_usage=true，
                // 末块才返回完整 usage（含 prompt_tokens_details.cached_tokens 缓存命中）。
                .streamUsage(true);

        // 推理强度（仅推理模型生效，非推理模型忽略）
        String reasoningEffort = chatModelConfig.getReasoningEffort();
        if (StringUtils.isNotBlank(reasoningEffort)) {
            optionsBuilder.reasoningEffort(reasoningEffort);
        }

        ChatModel rawChatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .build();

        //存到上下文中去
        dynamicContext.setChatModel(rawChatModel);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return agentNode;
    }

}
