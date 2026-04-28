package io.github.bridgewares.codebot.controller;

public record WebAskResponse(
        String question,
        String answer,
        boolean sentToGroup ) {
}
