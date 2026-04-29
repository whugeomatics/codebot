package io.github.bridgewares.codebot.qa;

import io.github.bridgewares.codebot.config.CodeBotProperties;
import io.github.bridgewares.codebot.wecom.WeComInboundMessage;
import io.github.bridgewares.codebot.wecom.WeComRobotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CodeQaService {

    private static final Logger log = LoggerFactory.getLogger(CodeQaService.class);
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\b(class|interface|enum)\\s+([A-Za-z0-9_]+)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("\\b(public|private|protected)\\s+[A-Za-z0-9_<>, ?\\[\\]]+\\s+([A-Za-z0-9_]+)\\s*\\(");

    private final CodeBotProperties properties;
    private final CodeIndexService codeIndexService;
    private final OpenAiCompatibleClient llmClient;
    private final WeComRobotClient robotClient;

    public CodeQaService(CodeBotProperties properties,
                         CodeIndexService codeIndexService,
                         OpenAiCompatibleClient llmClient,
                         WeComRobotClient robotClient) {
        this.properties = properties;
        this.codeIndexService = codeIndexService;
        this.llmClient = llmClient;
        this.robotClient = robotClient;
    }

    @Async
    public void answerAndSend(WeComInboundMessage message) {
        String question = message.normalizedQuestion();
        if (question.isBlank()) {
            robotClient.sendMarkdown("请在 @机器人 后面输入你要问的目标代码仓库问题。");
            return;
        }
        try {
            String answer = answer(question);
            robotClient.sendMarkdown("**代码问答**\n\n> " + escapeMarkdown(question) + "\n\n" + answer);
        } catch (Exception e) {
            log.error("Code QA failed", e);
            robotClient.sendMarkdown("代码问答失败：`" + escapeMarkdown(e.getMessage()) + "`");
        }
    }

    public String answer(String question) {
        if (!codeIndexService.ready()) {
            return "代码索引尚未就绪：`" + escapeMarkdown(codeIndexService.lastError()) + "`。请检查仓库配置后重新索引。";
        }
        List<SearchHit> hits = codeIndexService.search(question, properties.getIndex().getMaxResults());
        if (hits.isEmpty()) {
            return "没有在当前索引中找到明显相关代码。当前索引分支：`" + codeIndexService.branch() + "`。";
        }
        if (!llmClient.configured()) {
            return fallbackAnswer(hits);
        }
        String system = """
                你是目标代码仓库的代码问答助手。
                只基于用户提供的代码片段回答，不要编造不存在的文件、API 或配置。
                回答要用中文，先给结论，再列依据。
                提到代码依据时必须包含文件路径和行号范围。
                如果片段不足以确定答案，直接说明缺口，并给出下一步应该查哪个方向。
                """;
        String prompt = "当前分支: " + codeIndexService.branch()
                + "\n当前提交: " + codeIndexService.commitId()
                + "\n用户问题: " + question
                + "\n\n相关代码片段:\n" + snippets(hits);
        return llmClient.chat(system, prompt);
    }

    private String fallbackAnswer(List<SearchHit> hits) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前没有配置可用的大模型，以下是本地代码检索结果。\n\n")
                .append("**分支**：`").append(codeIndexService.branch()).append("`\n")
                .append("**提交**：`").append(codeIndexService.commitId()).append("`\n\n")
                .append("**最可能相关的位置**\n");

        for (SearchHit hit : hits.stream().limit(5).toList()) {
            CodeChunk chunk = hit.chunk();
            builder.append("\n")
                    .append("- `").append(chunk.displayPath()).append(":")
                    .append(chunk.startLine()).append("-").append(chunk.endLine()).append("`")
                    .append(" score=").append(String.format("%.1f", hit.score())).append("\n");
            String symbols = symbols(chunk.content());
            if (!symbols.isBlank()) {
                builder.append("  - 识别到：").append(symbols).append("\n");
            }
            String preview = preview(chunk.content(), 8);
            if (!preview.isBlank()) {
                builder.append("  - 片段：\n")
                        .append("```text\n")
                        .append(preview)
                        .append("\n```\n");
            }
        }
        builder.append("\n配置 `codebot.llm.api-key` 后，机器人会基于这些片段生成完整解释。");
        return builder.toString();
    }

    private String snippets(List<SearchHit> hits) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            CodeChunk chunk = hits.get(i).chunk();
            builder.append("\n--- snippet ").append(i + 1).append(" ---\n")
                    .append("file: ").append(chunk.displayPath()).append('\n')
                    .append("lines: ").append(chunk.startLine()).append("-").append(chunk.endLine()).append('\n')
                    .append(chunk.content()).append('\n');
        }
        return builder.toString();
    }

    private String escapeMarkdown(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String symbols(String content) {
        String classes = find(CLASS_PATTERN, content, 2, 3);
        String methods = find(METHOD_PATTERN, content, 2, 5);
        if (classes.isBlank() && methods.isBlank()) {
            return "";
        }
        if (methods.isBlank()) {
            return "类型 " + classes;
        }
        if (classes.isBlank()) {
            return "方法 " + methods;
        }
        return "类型 " + classes + "；方法 " + methods;
    }

    private String find(Pattern pattern, String content, int group, int limit) {
        Matcher matcher = pattern.matcher(content);
        return matcher.results()
                .map(result -> result.group(group))
                .distinct()
                .limit(limit)
                .collect(Collectors.joining(", "));
    }

    private String preview(String content, int maxLines) {
        return content.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("import "))
                .filter(line -> !line.startsWith("package "))
                .limit(maxLines)
                .collect(Collectors.joining("\n"));
    }
}
