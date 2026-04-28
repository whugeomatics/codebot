package io.github.bridgewares.codebot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "codebot")
public class CodeBotProperties {

    private boolean enabled = true;
    private Path repositoryPath = Path.of("../target-repository");
    private String branch = "main";
    private final WeCom wecom = new WeCom();
    private final Index index = new Index();
    private final LlModel llm = new LlModel();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getRepositoryPath() {
        return repositoryPath;
    }

    public void setRepositoryPath(Path repositoryPath) {
        this.repositoryPath = repositoryPath;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public WeCom getWecom() {
        return wecom;
    }

    public Index getIndex() {
        return index;
    }

    public LlModel getLlm() {
        return llm;
    }

    public static class WeCom {
        private String receiveId = "";
        private String token = "";
        private String encodingAesKey = "";
        private String webhookUrl = "";
        private boolean strictReceiveId = false;

        public String getReceiveId() {
            return receiveId;
        }

        public void setReceiveId(String receiveId) {
            this.receiveId = receiveId;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getEncodingAesKey() {
            return encodingAesKey;
        }

        public void setEncodingAesKey(String encodingAesKey) {
            this.encodingAesKey = encodingAesKey;
        }

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public boolean isStrictReceiveId() {
            return strictReceiveId;
        }

        public void setStrictReceiveId(boolean strictReceiveId) {
            this.strictReceiveId = strictReceiveId;
        }
    }

    public static class Index {
        private int chunkLines = 90;
        private int chunkOverlapLines = 12;
        private int maxFileBytes = 512 * 1024;
        private int maxResults = 8;
        private final List<String> includeExtensions = new ArrayList<>(List.of(
                ".java", ".xml", ".yml", ".yaml", ".properties", ".md", ".sql", ".txt"));

        public int getChunkLines() {
            return chunkLines;
        }

        public void setChunkLines(int chunkLines) {
            this.chunkLines = chunkLines;
        }

        public int getChunkOverlapLines() {
            return chunkOverlapLines;
        }

        public void setChunkOverlapLines(int chunkOverlapLines) {
            this.chunkOverlapLines = chunkOverlapLines;
        }

        public int getMaxFileBytes() {
            return maxFileBytes;
        }

        public void setMaxFileBytes(int maxFileBytes) {
            this.maxFileBytes = maxFileBytes;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }

        public List<String> getIncludeExtensions() {
            return includeExtensions;
        }
    }

    public static class LlModel {
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String model = "gpt-5.4";
        private Duration timeout = Duration.ofSeconds(180);
        private Integer maxTokens = 1200;
        private String thinkingType = "disabled";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public String getThinkingType() {
            return thinkingType;
        }

        public void setThinkingType(String thinkingType) {
            this.thinkingType = thinkingType;
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
