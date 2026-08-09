package com.gduf.domain.agent.service;

import com.gduf.domain.agent.model.entity.ChatCommandEntity;
import com.gduf.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;

import java.util.List;

public interface IChatService {

    List<AiAgentConfigTableVO.Agent> queryAiAgentConfig();

    String createSession(String agentId,String userId);

    List<String> handleMessage(String agentId,String userId,String message);

    List<String> handleMessage(String agentId,String userId,String sessionId,String message);

    Flowable<Event> handleMessageStream(String agentId,String userId,String sessionId,String message);

    List<String> handleMessage(ChatCommandEntity chatCommandEntity);
}
