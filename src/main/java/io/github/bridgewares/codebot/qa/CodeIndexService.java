package io.github.bridgewares.codebot.qa;

import io.github.bridgewares.codebot.config.CodeBotProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

@Service
public class CodeIndexService {

    private static final Logger log = LoggerFactory.getLogger(CodeIndexService.class);
    private static final Set<String> EXCLUDED_DIRS = Set.of(".git", ".idea", "target", "logs", "node_modules");
    private static final Set<String> SENSITIVE_KEYS = Set.of("password", "secret", "token", "webhook", "apikey", "api-key", "access_token");

    private final CodeBotProperties properties;
    private final List<CodeChunk> chunks = new CopyOnWriteArrayList<>();
    private volatile String branch = "";
    private volatile String commitId = "";

    public CodeIndexService(CodeBotProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (properties.isEnabled()) {
            reindex();
        }
    }

    public synchronized int reindex() {
        Path root = properties.getRepositoryPath().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Repository path does not exist: " + root);
        }
        List<CodeChunk> next = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !isExcluded(root, path))
                    .filter(this::isIncludedFile)
                    .filter(path -> isSmallEnough(path, properties.getIndex().getMaxFileBytes()))
                    .forEach(path -> next.addAll(chunkFile(root, path)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to index repository: " + root, e);
        }
        chunks.clear();
        chunks.addAll(next);
        branch = git(root, "rev-parse", "--abbrev-ref", "HEAD");
        commitId = git(root, "rev-parse", "HEAD");
        log.info("Indexed {} chunks from {}, branch={}, commit={}", chunks.size(), root, branch, commitId);
        return chunks.size();
    }

    public List<SearchHit> search(String question, int maxResults) {
        Set<String> queryTerms = TokenUtils.terms(question);
        return chunks.stream()
                .map(chunk -> new SearchHit(chunk, score(chunk, queryTerms, question)))
                .filter(hit -> hit.score() > 0)
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                .limit(maxResults)
                .toList();
    }

    public String branch() {
        return branch.isBlank() ? properties.getBranch() : branch;
    }

    public String commitId() {
        return commitId;
    }

    public int size() {
        return chunks.size();
    }

    private List<CodeChunk> chunkFile(Path root, Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<CodeChunk> result = new ArrayList<>();
            int chunkLines = Math.max(properties.getIndex().getChunkLines(), 20);
            int overlap = Math.max(properties.getIndex().getChunkOverlapLines(), 0);
            int step = Math.max(chunkLines - overlap, 1);
            for (int start = 0; start < lines.size(); start += step) {
                int end = Math.min(start + chunkLines, lines.size());
                List<String> slice = lines.subList(start, end);
                String content = redact(String.join("\n", slice));
                String displayPath = root.relativize(path).toString().replace('\\', '/');
                result.add(new CodeChunk(path, displayPath, start + 1, end, content,
                        TokenUtils.terms(displayPath + "\n" + content)));
                if (end == lines.size()) {
                    break;
                }
            }
            return result;
        } catch (Exception e) {
            log.debug("Skip unreadable file {}", path, e);
            return List.of();
        }
    }

    private double score(CodeChunk chunk, Set<String> queryTerms, String question) {
        double score = 0;
        for (String term : queryTerms) {
            if (chunk.terms().contains(term)) {
                score += term.length() > 2 ? 2 : 1;
            }
            if (chunk.displayPath().toLowerCase(Locale.ROOT).contains(term)) {
                score += 3;
            }
        }
        String lowerPath = chunk.displayPath().toLowerCase(Locale.ROOT);
        String lowerQuestion = question.toLowerCase(Locale.ROOT);
        if (lowerQuestion.contains("controller") && lowerPath.contains("/controller/")) {
            score += 4;
        }
        if (lowerQuestion.contains("service") && lowerPath.contains("/service/")) {
            score += 4;
        }
        if (lowerQuestion.contains("mapper") && lowerPath.contains("mapper")) {
            score += 4;
        }
        return score;
    }

    private boolean isExcluded(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            if (EXCLUDED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isIncludedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if ("pom.xml".equals(name)) {
            return true;
        }
        return properties.getIndex().getIncludeExtensions().stream().anyMatch(name::endsWith);
    }

    private boolean isSmallEnough(Path path, int maxBytes) {
        try {
            return Files.size(path) <= maxBytes;
        } catch (Exception e) {
            return false;
        }
    }

    private String redact(String content) {
        StringBuilder result = new StringBuilder(content.length());
        for (String line : content.split("\\R", -1)) {
            String lower = line.toLowerCase(Locale.ROOT);
            boolean sensitive = SENSITIVE_KEYS.stream().anyMatch(lower::contains);
            result.append(sensitive ? "[REDACTED SENSITIVE CONFIG LINE]" : line).append('\n');
        }
        return result.toString();
    }

    private String git(Path root, String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(List.of(args));
            Process process = new ProcessBuilder(command)
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String output = reader.readLine();
                int exit = process.waitFor();
                return exit == 0 && output != null ? output.trim() : "";
            }
        } catch (Exception e) {
            return "";
        }
    }
}
