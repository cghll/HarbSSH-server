package com.gduf.test.api.model;

import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * LangChain4j
 * <p>
 * 文档：<a href="https://docs.langchain4j.info/">langchain4j</a>
 * 案例：<a href="https://github.com/langchain4j/langchain4j-examples">langchain4j-examples</a>
 *
 * @author cgh
 * 2025/12/14 09:20
 */
@Slf4j
public class LangChain4jApiTest {

    public static void main(String[] args) throws IOException {
        Path configPath = Paths.get("ai-config.properties");
        if (!Files.exists(configPath)) {
            throw new IllegalStateException("请在项目根目录下创建 ai-config.properties 文件，配置 ai.api.key");
        }
        Properties config = new Properties();
        try (InputStream is = new FileInputStream(configPath.toFile())) {
            config.load(is);
        }

        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://apis.itedus.cn/v1")
                .apiKey(config.getProperty("ai.api.key"))
                .modelName("gpt-4o")
                .build();

        String chat = model.chat("hi 你好哇!");
        log.info("测试结果:{}", chat);
    }

}
