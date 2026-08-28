package com.gduf.test.domain.agent;


import com.gduf.domain.agent.model.valobj.AiAgentRegisterVO;
import com.gduf.domain.agent.model.valobj.intent.IntentResultVO;
import com.gduf.domain.agent.service.IIntentService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

/**
 * 意图识别服务测试
 * <p>
 * 验证规则层与 LLM 层级联的意图识别效果，覆盖诊断、配置、部署、监控、
 * 安全、备份、解释、搜索、执行、闲聊、继续等场景。
 *
 * @author walissh dev
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
/**
 * 注意：此为依赖真实 LLM API 的集成测试，运行需满足：
 * 1. 网络可访问 application-dev.yml 中 intent-ai-api.base-url；
 * 2. surefire 配置 -javaagent 指向 byte-buddy-agent（JDK 24+ 的 mockito self-attach 限制）。
 * 规则层/解析逻辑的无网络单测见 walissh-server-domain 模块。
 */
//@Ignore("集成测试：需联网与 JDK agent 配置，默认跳过")
public class IntentServiceTest {

    @Resource
    private IIntentService intentService;

    @Resource
    private ApplicationContext applicationContext;

    private static final String SESSION_ID = "intent-test-session";
    private static final String USER_ID = "intent-test-user";

    @Before
    public void configureLlmClassifier() {
        // 复用 ssh-agent 装配好的 OpenAiApi 与模型名，注入 LLM 意图分类器，使其真正生效
        // agent-id 见 application-dev.yml 导入的 agent/ssh-agent.yml
        AiAgentRegisterVO agent = applicationContext.getBean("100000", AiAgentRegisterVO.class);
        if (agent != null && agent.getOpenAiApi() != null) {
            intentService.configure(agent.getOpenAiApi(), agent.getChatModelName());
        }
    }

    private void classifyAndLog(String message) {
        IntentResultVO result = intentService.classify(SESSION_ID, USER_ID, message);
        log.info("输入: [{}]\n  -> 意图: {} | 置信度: {} | 实体: {} | raw: {}",
                message,
                result.getIntent().getLabel(),
                result.getConfidence(),
                result.getEntities(),
                result.getRawResponse());
    }

    @Test
    public void test_intent_classify() {
        log.info("========== 意图识别测试开始 ==========");

        // 规则层应能直接命中的高置信场景
        classifyAndLog("nginx 502了，帮我看看为什么挂了");
        classifyAndLog("帮我改下 redis 的 maxmemory 配置");
        classifyAndLog("部署新版本到生产环境");
        classifyAndLog("看下服务器磁盘使用情况");
        classifyAndLog("检查防火墙规则是否开放了80端口");
        classifyAndLog("备份一下数据库");
        classifyAndLog("这个命令 awk '{print $1}' access.log 是什么意思");
        classifyAndLog("找一下占用8080端口的进程");

        // 规则层置信度不足、需要 LLM 兜底的场景
        classifyAndLog("帮我跑一下 df -h");
        classifyAndLog("今天天气怎么样");
        classifyAndLog("继续刚才的操作");
        classifyAndLog("服务器好像有点慢，帮我瞧瞧");
        classifyAndLog("怎么把日志按时间倒序排一下");

        log.info("========== 意图识别测试结束 ==========");
    }
}
