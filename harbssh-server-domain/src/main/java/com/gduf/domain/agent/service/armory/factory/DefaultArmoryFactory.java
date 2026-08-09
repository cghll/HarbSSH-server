package com.gduf.domain.agent.service.armory.factory;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.gduf.domain.agent.model.entity.ArmoryCommandEntity;
import com.gduf.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.gduf.domain.agent.model.valobj.AiAgentRegisterVO;
import com.gduf.domain.agent.service.armory.node.RootNode;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.SequentialAgent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DefaultArmoryFactory {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private RootNode rootNode;

    public StrategyHandler<ArmoryCommandEntity, DynamicContext, AiAgentRegisterVO> armoryStrategyHandler() {
        return rootNode;
    }

    public AiAgentRegisterVO getAiAgentRegisterVO(String agentId){
        return  applicationContext.getBean(agentId,AiAgentRegisterVO.class);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DynamicContext {
        /**
         * LLM API
         */
        private OpenAiApi openAiApi;
        /**
         * LLM  chatModel
         */
        private ChatModel chatModel;


        /**
         * 智能体配置组
         */
        private Map<String, BaseAgent> agentGroup=new HashMap<>();

        //计数器
        private AtomicInteger currentStepIndex=new AtomicInteger(0);
        //当前的智能体
        private AiAgentConfigTableVO.Module.AgentWorkflow currentAgentWorkflow;

        private Map<String, Object> dataObjects = new HashMap<>();

        public <T> void setValue(String key, T value) {
            dataObjects.put(key, value);
        }

        public <T> T getValue(String key) {
            return (T) dataObjects.get(key);
        }

        public List<BaseAgent> querryAgentList(List<String> agentNames){
            if(agentNames==null||agentNames.isEmpty()||agentGroup==null){
                return Collections.emptyList();
            }
            List<BaseAgent> agents=new ArrayList<>();
            for(String agentName:agentNames){
                BaseAgent agent = agentGroup.get(agentName);
                if (agent != null){
                    agents.add(agent);
                }
            }
            return agents;
        }

        public void addCurrentStepIndex(){
            currentStepIndex.incrementAndGet();
        }
        public int getCurrentStepIndex(){
            return currentStepIndex.get();
        }
    }
}
