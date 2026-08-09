package com.gduf.domain.agent.model.valobj;

import com.google.adk.runner.InMemoryRunner;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiAgentRegisterVO {
    /**
     * 应用名称
     */
    private String appName;
    /**
     * 智能体id
     */
    private String agentId;
    /**
     * 智能体名称
     */
    private String agentName;
    /**
     * 智能体描述
     */
    private String agentDesc;
    /**
     * 智能体执行对象
     */
    private InMemoryRunner runner;
}
