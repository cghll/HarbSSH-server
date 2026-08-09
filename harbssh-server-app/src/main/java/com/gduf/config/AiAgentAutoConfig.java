package com.gduf.config;

import com.alibaba.fastjson.JSON;
import com.gduf.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import com.gduf.domain.agent.service.IArmoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;

@Slf4j
@Configuration
@EnableConfigurationProperties(AiAgentAutoConfigProperties.class)
public class AiAgentAutoConfig implements ApplicationListener<ApplicationReadyEvent> {
    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;
    @Resource
    private IArmoryService armoryService;

    //输出日志
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            log.info("Ai agent智能体装配{}", JSON.toJSONString(aiAgentAutoConfigProperties.getTables().values()));
            armoryService.acceptArmoryAgents(new ArrayList<>(aiAgentAutoConfigProperties.getTables().values()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
