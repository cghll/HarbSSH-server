package com.gduf.test.api.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Slf4j
public class SpringAiApiTest {

    public static void main(String[] args) throws IOException {
        Path configPath = Paths.get("ai-config.properties");
        if (!Files.exists(configPath)) {
            throw new IllegalStateException("请在项目根目录下创建 ai-config.properties 文件，配置 ai.api.key");
        }
        Properties config = new Properties();
        try (InputStream is = new FileInputStream(configPath.toFile())) {
            config.load(is);
        }

        String apiKey = config.getProperty("ai.api.key");
        log.info("读取到的 key: {}...{} (长度:{})", apiKey.substring(0, 5), apiKey.substring(apiKey.length() - 5), apiKey.length());

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(apiKey)
                .completionsPath("v1/chat/completions")
                .embeddingsPath("v1/embeddings")
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("deepseek-chat")
                        .build())
                .build();

        String call = chatModel.call("hi 你好哇!");

        log.info("测试结果:{}", call);
    }

}
