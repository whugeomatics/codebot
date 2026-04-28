package io.github.bridgewares.codebot;

import io.github.bridgewares.codebot.config.CodeBotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties(CodeBotProperties.class)
public class CodeBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeBotApplication.class, args);
    }
}
