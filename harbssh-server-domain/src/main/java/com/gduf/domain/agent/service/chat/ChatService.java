package com.gduf.domain.agent.service.chat;

import com.gduf.domain.agent.adapter.repository.IChatHistoryRepository;
import com.gduf.domain.agent.model.entity.ChatCommandEntity;
import com.gduf.domain.agent.model.entity.ChatMessageEntity;
import com.gduf.domain.agent.model.entity.ChatSessionEntity;
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

    @Resource
    private IChatHistoryRepository chatHistoryRepository;

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
        String sessionKey = agentId + ":" + userId;

        return userSessions.computeIfAbsent(sessionKey,key->{
            Session session = runner.sessionService().createSession(appName, userId)
                    .blockingGet();

            // 会话元数据落库（新增）：try-catch 旁路写入，DB 写入失败不影响 ADK Session 创建。
            // 旁路原则：如果 DB 异常（如表不存在），只是 DB 里没有这条记录，不影响 Agent 正常运行。
            try {
                chatHistoryRepository.saveSession(ChatSessionEntity.builder()
                        .id(session.id())
                        .agentId(agentId)
                        .userId(userId)
                        .title("新会话")
                        .messageCount(0)
                        .build());
            } catch (Exception e) {
                log.error("保存会话元数据失败 sessionId={}", session.id(), e);
            }

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

    /**
     * 查询用户会话列表（新增）。
     * <p>
     * 为前端历史记录功能提供后端支撑，默认返回最多 20 条。
     *
     * @param agentId 智能体 ID
     * @param userId  用户 ID
     * @param limit   最大返回条数，≤0 时默认 20
     * @return 会话列表
     */
    @Override
    public List<ChatSessionEntity> querySessionList(String agentId, String userId, int limit) {
        return chatHistoryRepository.querySessionList(agentId, userId, limit > 0 ? limit : 20);
    }

    /**
     * 查询会话消息列表（新增）。
     * <p>
     * 为前端历史消息展示提供后端支撑，默认返回最多 100 条。
     *
     * @param sessionId 会话 ID
     * @param limit     最大返回条数，≤0 时默认 100
     * @return 消息列表（时间正序）
     */
    @Override
    public List<ChatMessageEntity> queryMessageList(String sessionId, int limit) {
        return chatHistoryRepository.queryMessageList(sessionId, limit > 0 ? limit : 100);
    }
}
