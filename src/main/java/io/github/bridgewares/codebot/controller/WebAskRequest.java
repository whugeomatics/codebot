package io.github.bridgewares.codebot.controller;

import jakarta.validation.constraints.NotBlank;

public record WebAskRequest(
        @NotBlank(message = "question is required")
        String question,
        String askedBy,
        boolean sendToGroup
) {
}
