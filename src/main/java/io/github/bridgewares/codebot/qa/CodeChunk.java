package io.github.bridgewares.codebot.qa;

import java.nio.file.Path;
import java.util.Set;

public record CodeChunk(
        Path file,
        String displayPath,
        int startLine,
        int endLine,
        String content,
        Set<String> terms
) {
}
