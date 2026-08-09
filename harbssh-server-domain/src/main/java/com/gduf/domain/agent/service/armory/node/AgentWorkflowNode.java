package com.gduf.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.gduf.domain.agent.model.entity.ArmoryCommandEntity;
import com.gduf.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.gduf.domain.agent.model.valobj.AiAgentRegisterVO;
import com.gduf.domain.agent.model.valobj.enums.AgentTypeEnum;
import com.gduf.domain.agent.service.armory.AbstractArmorySupport;
import com.gduf.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.gduf.domain.agent.service.armory.node.workflow.LoopAgentNode;
import com.gduf.domain.agent.service.armory.node.workflow.ParallelAgentNode;
import com.gduf.domain.agent.service.armory.node.workflow.SequentialAgentNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AgentWorkflowNode extends AbstractArmorySupport {
    @Resource
    private LoopAgentNode loopAgentNode;
    @Resource
    private ParallelAgentNode parallelAgentNode;
    @Resource
    private SequentialAgentNode sequentialAgentNode;
    @Resource
    private RunnerNode runnerNode;
    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("AI Agent 装配操作-AgentWorkflowNode");
        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        List<AiAgentConfigTableVO.Module.AgentWorkflow> agentWorkflows = aiAgentConfigTableVO.getModule().getAgentWorkflows();
        //当前步长索引大于agentWorkflows数也流转走
        if(agentWorkflows==null||agentWorkflows.isEmpty()||dynamicContext.getCurrentStepIndex()>=agentWorkflows.size()){
            //设置结果值，清空
            dynamicContext.setCurrentAgentWorkflow(null);
            //为空直接流转走
            return router(requestParameter, dynamicContext);
        }

        dynamicContext.setCurrentAgentWorkflow(agentWorkflows.get(dynamicContext.getCurrentStepIndex()));
        //增加步长
        dynamicContext.addCurrentStepIndex();

        return router(requestParameter, dynamicContext);

    }

    /**
     * 根据type走对应节点类型，主流转节点
     */
    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        AiAgentConfigTableVO.Module.AgentWorkflow currentAgentWorkflow = dynamicContext.getCurrentAgentWorkflow();
        if(currentAgentWorkflow==null){
            return runnerNode;
        }
        String type = currentAgentWorkflow.getType();
        AgentTypeEnum agentTypeEnum = AgentTypeEnum.getByType(type);
        if (agentTypeEnum == null){
            throw new RuntimeException("agentWorkflow type is error");
        }
        String node = agentTypeEnum.getNode();
        return switch (node){
            case "loopAgentNode" -> loopAgentNode;
            case "parallelAgentNode" -> parallelAgentNode;
            case "sequentialAgentNode" -> sequentialAgentNode;
            default -> runnerNode;
        };
    }
}
