package com.gduf.domain.agent.service.chat;

import com.gduf.domain.agent.model.entity.ChatCommandEntity;
import com.gduf.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.gduf.domain.agent.model.valobj.AiAgentRegisterVO;
import com.gduf.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import com.gduf.domain.agent.service.IChatService;
import com.gduf.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.gduf.domain.agent.service.armory.matter.tools.SshExecuteAdkTool;
import com.gduf.types.enums.ResponseCode;
import com.gduf.types.exception.AppException;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatService implements IChatService {

    @Resource
    private DefaultArmoryFactory defaultArmoryFactory;
    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;
    private final Map<String,String> userSessions=new ConcurrentHashMap<>();
    @Override
    public List<AiAgentConfigTableVO.Agent> queryAiAgentConfig() {
        Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();
        List<AiAgentConfigTableVO.Agent> agentList=new ArrayList<>();
        if(tables!=null){
            for (AiAgentConfigTableVO vo : tables.values()){
                if(vo!=null){
                    agentList.add(vo.getAgent());
                }
            }
        }
        return agentList;
    }

    @Override
    public String createSession(String agentId, String userId) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);
        if(aiAgentRegisterVO==null){
            throw new AppException(ResponseCode.E0001.getCode());
        }
        String appName = aiAgentRegisterVO.getAppName();
        Runner runner = aiAgentRegisterVO.getRunner();
        return userSessions.computeIfAbsent(userId,uId->{
            Session session = runner.sessionService().createSession(appName, uId)
                    .blockingGet();
            return session.id();
        });
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String message) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);
        if(aiAgentRegisterVO==null){
            throw new AppException(ResponseCode.E0001.getCode());
        }
        String sessionId = createSession(agentId, userId);
        return  handleMessage(agentId, userId, sessionId, message);
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String sessionId, String message) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);
        if(aiAgentRegisterVO==null){
            throw new AppException(ResponseCode.E0001.getCode());
        }
        Runner runner = aiAgentRegisterVO.getRunner();
        Content userMsg = Content.fromParts(Part.fromText(message));
        Flowable<Event> events = runner.runAsync(userId, sessionId, userMsg);

        List<String> outputs=new ArrayList<>();
        events.blockingForEach(event -> {outputs.add(event.stringifyContent());});
        return outputs;
    }

    @Override
    public Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);
        if(aiAgentRegisterVO==null){
            throw new AppException(ResponseCode.E0001.getCode());
        }
        Runner runner = aiAgentRegisterVO.getRunner();
        Content userMsg = Content.fromParts(Part.fromText(message));
        return runner.runAsync(userId, sessionId, userMsg);
    }

    @Override
    public Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message, String terminalSessionId) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);
        if(null==aiAgentRegisterVO){
            throw new AppException(ResponseCode.E0001.getCode());
        }
        Runner runner = aiAgentRegisterVO.getRunner();

        // 设置终端会话ID到ThreadLocal，供工具使用
        if(terminalSessionId!=null&&!terminalSessionId.isEmpty()){
            log.info("设置终端会话ID: {}", terminalSessionId);
            SshExecuteAdkTool.setCurrentTerminalSession(terminalSessionId);
        }

        Content userMsg = Content.fromParts(Part.fromText(message));

        return runner.runAsync(userId,sessionId,userMsg);
    }

    @Override
    public List<String> handleMessage(ChatCommandEntity chatCommandEntity) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(chatCommandEntity.getAgentId());
        if(aiAgentRegisterVO==null){
            throw new AppException(ResponseCode.E0001.getCode());
        }
        List<Part> parts = new ArrayList<>();

        List<ChatCommandEntity.Content.Text> texts = chatCommandEntity.getTexts();
        if (null != texts && !texts.isEmpty()) {
            for (ChatCommandEntity.Content.Text text : texts) {
                parts.add(Part.fromText(text.getMessage()));
            }
        }

        List<ChatCommandEntity.Content.File> files = chatCommandEntity.getFiles();
        if (null != files && !files.isEmpty()) {
            for (ChatCommandEntity.Content.File file : files) {
                parts.add(Part.fromUri(file.getFileUri(), file.getMimeType()));
            }
        }

        List<ChatCommandEntity.Content.InlineData> inlineDataList = chatCommandEntity.getInlineDataList();
        if (null != inlineDataList && !inlineDataList.isEmpty()) {
            for (ChatCommandEntity.Content.InlineData inlineData : inlineDataList) {
                parts.add(Part.fromBytes(inlineData.getBytes(), inlineData.getMimeType()));
            }
        }

        Content content = Content.builder().role("user").parts(parts).build();

        Runner runner = aiAgentRegisterVO.getRunner();
        Flowable<Event> events = runner.runAsync(chatCommandEntity.getUserId(), chatCommandEntity.getSessionId(), content);

        List<String> outputs=new ArrayList<>();
        events.blockingForEach(event -> {outputs.add(event.stringifyContent());});
        return outputs;
    }
}
