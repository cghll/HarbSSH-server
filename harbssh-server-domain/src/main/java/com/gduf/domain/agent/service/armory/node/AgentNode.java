package com.gduf.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.gduf.domain.agent.model.entity.ArmoryCommandEntity;
import com.gduf.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.gduf.domain.agent.model.valobj.AiAgentRegisterVO;
import com.gduf.domain.agent.service.armory.AbstractArmorySupport;
import com.gduf.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.gduf.domain.agent.service.armory.matter.session.factory.CustomRunnerFactory;
import com.gduf.domain.agent.service.armory.matter.tools.SshExecuteAdkTool;
import com.gduf.domain.agent.service.armory.matter.tools.SubAgentDispatchTool;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.springai.SpringAI;
import com.google.adk.tools.FunctionTool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AgentNode extends AbstractArmorySupport {
    @Resource
    private AgentWorkflowNode agentWorkflowNode;

    @Resource
    private SshExecuteAdkTool sshExecuteAdkTool;

    @javax.annotation.Resource
    private CustomRunnerFactory customRunnerFactory;
    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("AI Agent 装配操作-AgentNode");
        ChatModel chatModel = dynamicContext.getChatModel();

        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        List<AiAgentConfigTableVO.Module.Agent> agents = aiAgentConfigTableVO.getModule().getAgents();
        for(AiAgentConfigTableVO.Module.Agent agentConfig:agents){
            LlmAgent.Builder builder=LlmAgent.builder()
                    .name(agentConfig.getName())
                    .description(agentConfig.getDescription())
                    .model(new SpringAI(chatModel))
                    .instruction(agentConfig.getInstruction())
                    .outputKey(agentConfig.getOutputKey());

            //构建ADK工具列表，- 注意，这部分也可以提炼到配置文件
            List<Object> adkTools = new ArrayList<>();

            // 添加 SSH 执行工具（ADK 原生 FunctionTool）
            try {
                log.info("开始创建 SSH 执行工具, sshExecuteAdkTool={}", sshExecuteAdkTool);
                //Google ADK 通过反射读取** executeCommand **方法上的** @Schema **注解，生成** FunctionDeclaration**，包装成** FunctionTool**。
                FunctionTool sshTool = FunctionTool.create(sshExecuteAdkTool, "executeCommand");
                log.info("FunctionTool 创建成功: name={}, declaration={}",
                        sshTool.name(),
                        sshTool.declaration().isPresent() ? sshTool.declaration().get() : "null");
                adkTools.add(sshTool);
                log.info("为 Agent [{}] 注册 SSH 执行工具成功", agentConfig.getName());
            } catch (Exception e) {
                log.error("创建 SSH ADK 工具失败", e);
            }

            // 注册工具到 Agent
            if (!adkTools.isEmpty()) {
                log.info("为 Agent [{}] 注册 {} 个工具", agentConfig.getName(), adkTools.size());
                builder.tools(adkTools);
            } else {
                log.warn("Agent [{}] 没有注册任何工具！", agentConfig.getName());
            }

            LlmAgent llmAgent = builder.build();

            dynamicContext.getAgentGroup().put(agentConfig.getName(),llmAgent);
        }
        return router(requestParameter, dynamicContext);
    }

    /**
     * 为配置了 subAgents 的父 Agent 重新装配"多 Agent 派发"能力：
     * <ol>
     *   <li>把每个声明的子 Agent 包装成 {@link SubAgentDispatchTool}（单 Agent 派发工具）</li>
     *   追加 {@link BatchSubAgentDispatchTool}（主 Agent 自行拆解任务的批量派发工具）</li>
     *   <li>追加 {@link DynamicPlanDispatchTool}（由独立规划器生成计划的动态派发工具）</li>
     * </ol>
     * 用同一套配置重建父 Agent 并覆盖 agentGroup，使其获得派发类工具；
     * 子 Agent 未在配置中声明时直接抛出异常，fail-fast。
     */
    private void buildAgentTools(DefaultArmoryFactory.DynamicContext dynamicContext,
                                 List<AiAgentConfigTableVO.Module.Agent> agents,
                                 ChatModel chatModel,
                                 String modelName)throws Exception{
        Map<String, BaseAgent> agentGroup = dynamicContext.getAgentGroup();

        for (AiAgentConfigTableVO.Module.Agent agentConfig : agents) {
            // 未声明 subAgents 的 Agent 不需要派发能力，跳过重建
            List<String> subAgentNames = agentConfig.getSubAgents();
            if (subAgentNames == null || subAgentNames.isEmpty()) {
                continue;
            }

            List<Object> adkTools = new ArrayList<>();

            // 为每个声明的子 Agent 构建单独的派发工具（工具名即子 Agent 名，LLM 可直接点名调用）
            for (String subAgentName : subAgentNames) {
                BaseAgent subAgent = agentGroup.get(subAgentName);
                if (subAgent == null) {
                    throw new IllegalArgumentException(
                            "sub agent not found: " + subAgentName);
                }
                adkTools.add(new SubAgentDispatchTool(subAgent, customRunnerFactory));
            }

            // 批量派发工具：主 Agent 自行拆解任务列表并发派发
            adkTools.add(new BatchSubAgentDispatchTool(
                    agents.stream().map(AiAgentConfigTableVO.Module.Agent::getName).toList(),
                    dynamicAgentOrchestrator));
        }


    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return agentWorkflowNode;
    }
}
