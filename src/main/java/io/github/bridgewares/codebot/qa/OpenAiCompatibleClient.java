package io.github.bridgewares.codebot.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bridgewares.codebot.config.CodeBotProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleClient {

    private final CodeBotProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleClient(CodeBotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public boolean configured() {
        return properties.getLlm().isConfigured();
    }

    public String chat(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", properties.getLlm().getModel());
            body.put("temperature", 0.1);
            if (properties.getLlm().getMaxTokens() != null && properties.getLlm().getMaxTokens() > 0) {
                body.put("max_tokens", properties.getLlm().getMaxTokens());
            }
            if (properties.getLlm().getThinkingType() != null && !properties.getLlm().getThinkingType().isBlank()) {
                body.put("thinking", Map.of("type", properties.getLlm().getThinkingType()));
            }
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
            String json = objectMapper.writeValueAsString(body);
            String url = properties.getLlm().getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(properties.getLlm().getTimeout())
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getLlm().getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("LLM request failed: " + response.statusCode() + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");
            String content = firstText(
                    message.path("content"),
                    message.path("reasoning_content"),
                    message.path("reasoning"),
                    choice.path("text")
            );
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("LLM returned empty content, finish_reason="
                        + choice.path("finish_reason").asText("")
                        + ", response=" + abbreviate(response.body(), 1200));
            }
            return content;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to call OpenAI-compatible chat API", e);
        }
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }
            if (node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
            if (!node.isTextual() && !node.toString().isBlank() && !"null".equals(node.toString())) {
                return node.toString();
            }
        }
        return "";
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
