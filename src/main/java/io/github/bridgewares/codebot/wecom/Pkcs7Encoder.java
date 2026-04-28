package io.github.bridgewares.codebot.wecom;

import java.util.Arrays;

final class Pkcs7Encoder {

    private static final int BLOCK_SIZE = 32;

    private Pkcs7Encoder() {
    }

    static byte[] encode(byte[] bytes) {
        int amountToPad = BLOCK_SIZE - (bytes.length % BLOCK_SIZE);
        if (amountToPad == 0) {
            amountToPad = BLOCK_SIZE;
        }
        byte[] result = Arrays.copyOf(bytes, bytes.length + amountToPad);
        Arrays.fill(result, bytes.length, result.length, (byte) amountToPad);
        return result;
    }

    static byte[] decode(byte[] decrypted) {
        if (decrypted.length == 0) {
            throw new WeComCryptoException("Empty decrypted payload");
        }
        int pad = decrypted[decrypted.length - 1] & 0xff;
        if (pad < 1 || pad > BLOCK_SIZE) {
            pad = 0;
        }
        return Arrays.copyOf(decrypted, decrypted.length - pad);
    }
}
