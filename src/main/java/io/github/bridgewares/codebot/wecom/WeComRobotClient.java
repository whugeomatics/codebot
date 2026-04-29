package io.github.bridgewares.codebot.wecom;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bridgewares.codebot.config.CodeBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class WeComRobotClient {

    private static final Logger log = LoggerFactory.getLogger(WeComRobotClient.class);
    private static final int MAX_MARKDOWN_LENGTH = 3900;

    private final CodeBotProperties properties;
    private final ObjectMapper objectMapper;
    private volatile HttpClient httpClient;

    public WeComRobotClient(CodeBotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void sendMarkdown(String markdown) {
        String webhookUrl = properties.getWecom().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Skip WeCom robot send because codebot.wecom.webhook-url is empty");
            return;
        }
        try {
            String content = truncate(markdown);
            String body = objectMapper.writeValueAsString(Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of("content", content)
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("WeCom robot send failed, status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("WeCom robot send failed", e);
        }
    }

    private String truncate(String markdown) {
        if (markdown == null) {
            return "";
        }
        if (markdown.length() <= MAX_MARKDOWN_LENGTH) {
            return markdown;
        }
        return markdown.substring(0, MAX_MARKDOWN_LENGTH - 40) + "\n\n...内容过长，已截断";
    }

    private HttpClient httpClient() {
        HttpClient current = httpClient;
        if (current == null) {
            current = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            httpClient = current;
        }
        return current;
    }
}
