package io.github.bridgewares.codebot.controller;

import io.github.bridgewares.codebot.config.CodeBotProperties;
import io.github.bridgewares.codebot.qa.CodeIndexService;
import io.github.bridgewares.codebot.qa.CodeQaService;
import io.github.bridgewares.codebot.wecom.WeComRobotClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/code-bot")
public class AdminController {

    private static final String ADMIN_TOKEN_HEADER = "X-CodeBot-Admin-Token";

    private final CodeBotProperties properties;
    private final CodeIndexService codeIndexService;
    private final CodeQaService codeQaService;
    private final WeComRobotClient robotClient;

    public AdminController(CodeBotProperties properties,
                           CodeIndexService codeIndexService,
                           CodeQaService codeQaService,
                           WeComRobotClient robotClient) {
        this.properties = properties;
        this.codeIndexService = codeIndexService;
        this.codeQaService = codeQaService;
        this.robotClient = robotClient;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", codeIndexService.ready() ? "UP" : "NOT_READY",
                "branch", codeIndexService.branch(),
                "commitId", codeIndexService.commitId(),
                "chunks", codeIndexService.size(),
                "lastError", codeIndexService.lastError()
        );
    }

    @PostMapping("/admin/reindex")
    public Map<String, Object> reindex(@RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String adminToken) {
        requireAdmin(adminToken);
        int chunks = codeIndexService.reindex();
        return Map.of("chunks", chunks, "branch", codeIndexService.branch(), "commitId", codeIndexService.commitId());
    }

    @GetMapping("/debug/ask")
    public Map<String, String> ask(@RequestParam String question,
                                   @RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String adminToken) {
        requireAdmin(adminToken);
        return Map.of("answer", codeQaService.answer(question));
    }

    @PostMapping("/web/ask")
    public WebAskResponse webAsk(@Valid @RequestBody WebAskRequest request) {
        String answer = codeQaService.answer(request.question());
        boolean sendToGroup = request.sendToGroup();
        if (sendToGroup) {
            robotClient.sendMarkdown(formatGroupMessage(request, answer));
        }
        return new WebAskResponse(request.question(), answer, sendToGroup);
    }

    private String formatGroupMessage(WebAskRequest request, String answer) {
        String askedBy = request.askedBy() == null || request.askedBy().isBlank()
                ? "网页提问"
                : request.askedBy().trim();
        return "**代码问答**\n\n"
                + "**提问人**：" + escapeMarkdown(askedBy) + "\n\n"
                + "> " + escapeMarkdown(request.question()) + "\n\n"
                + answer;
    }

    private String escapeMarkdown(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void requireAdmin(String providedToken) {
        String expectedToken = properties.getAdminToken();
        if (expectedToken == null || expectedToken.isBlank()) {
            return;
        }
        if (!expectedToken.equals(providedToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin token");
        }
    }
}
