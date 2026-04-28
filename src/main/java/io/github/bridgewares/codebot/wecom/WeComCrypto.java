package io.github.bridgewares.codebot.wecom;

import io.github.bridgewares.codebot.config.CodeBotProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;

@Component
public class WeComCrypto {

    private final CodeBotProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public WeComCrypto(CodeBotProperties properties) {
        this.properties = properties;
    }

    public String decryptEcho(String msgSignature, String timestamp, String nonce, String echoStr) {
        verifySignature(msgSignature, timestamp, nonce, echoStr);
        return decrypt(echoStr);
    }

    public String decryptMessage(String msgSignature, String timestamp, String nonce, String encryptedXml) {
        String encrypt = XmlUtils.getText(encryptedXml, "Encrypt");
        verifySignature(msgSignature, timestamp, nonce, encrypt);
        return decrypt(encrypt);
    }

    public String encryptResponse(String plainXml, String timestamp, String nonce) {
        String encrypt = encrypt(plainXml);
        String signature = signature(timestamp, nonce, encrypt);
        return "<xml>"
                + "<Encrypt><![CDATA[" + encrypt + "]]></Encrypt>"
                + "<MsgSignature><![CDATA[" + signature + "]]></MsgSignature>"
                + "<TimeStamp>" + timestamp + "</TimeStamp>"
                + "<Nonce><![CDATA[" + nonce + "]]></Nonce>"
                + "</xml>";
    }

    public void verifySignature(String expected, String timestamp, String nonce, String encrypt) {
        String actual = signature(timestamp, nonce, encrypt);
        if (!actual.equals(expected)) {
            throw new WeComCryptoException("Invalid WeCom callback signature");
        }
    }

    private String signature(String timestamp, String nonce, String encrypt) {
        String token = properties.getWecom().getToken();
        String[] values = {token, timestamp, nonce, encrypt};
        Arrays.sort(values, Comparator.naturalOrder());
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(String.join("", values).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new WeComCryptoException("Unable to calculate WeCom signature", e);
        }
    }

    private String decrypt(String encrypted) {
        try {
            byte[] aesKey = aesKey();
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(aesKey, 0, 16));
            byte[] original = Pkcs7Encoder.decode(cipher.doFinal(Base64.getDecoder().decode(encrypted)));

            int xmlLength = ByteBuffer.wrap(original, 16, 4).getInt();
            String xml = new String(original, 20, xmlLength, StandardCharsets.UTF_8);
            String receiveId = new String(original, 20 + xmlLength, original.length - 20 - xmlLength, StandardCharsets.UTF_8);
            validateReceiveId(receiveId);
            return xml;
        } catch (WeComCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new WeComCryptoException("Unable to decrypt WeCom message", e);
        }
    }

    private String encrypt(String plainXml) {
        try {
            byte[] aesKey = aesKey();
            byte[] random = new byte[16];
            secureRandom.nextBytes(random);
            byte[] xmlBytes = plainXml.getBytes(StandardCharsets.UTF_8);
            byte[] receiveIdBytes = properties.getWecom().getReceiveId().getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(20 + xmlBytes.length + receiveIdBytes.length);
            buffer.put(random);
            buffer.putInt(xmlBytes.length);
            buffer.put(xmlBytes);
            buffer.put(receiveIdBytes);

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(aesKey, 0, 16));
            return Base64.getEncoder().encodeToString(cipher.doFinal(Pkcs7Encoder.encode(buffer.array())));
        } catch (Exception e) {
            throw new WeComCryptoException("Unable to encrypt WeCom response", e);
        }
    }

    private byte[] aesKey() {
        String key = properties.getWecom().getEncodingAesKey();
        if (key == null || key.length() != 43) {
            throw new WeComCryptoException("codebot.wecom.encoding-aes-key must be 43 characters");
        }
        return Base64.getDecoder().decode(key + "=");
    }

    private void validateReceiveId(String receiveId) {
        String configured = properties.getWecom().getReceiveId();
        if (!properties.getWecom().isStrictReceiveId() || configured == null || configured.isBlank()) {
            return;
        }
        if (!configured.equals(receiveId)) {
            throw new WeComCryptoException("Unexpected WeCom receive id");
        }
    }
}
