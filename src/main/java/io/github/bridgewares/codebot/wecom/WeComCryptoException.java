package io.github.bridgewares.codebot.wecom;

public class WeComCryptoException extends RuntimeException {

    public WeComCryptoException(String message) {
        super(message);
    }

    public WeComCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
