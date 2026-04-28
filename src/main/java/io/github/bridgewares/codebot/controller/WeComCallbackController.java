package io.github.bridgewares.codebot.controller;

import io.github.bridgewares.codebot.qa.CodeQaService;
import io.github.bridgewares.codebot.wecom.WeComCrypto;
import io.github.bridgewares.codebot.wecom.WeComInboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/code-bot/wecom/callback")
public class WeComCallbackController {

    private static final Logger log = LoggerFactory.getLogger(WeComCallbackController.class);

    private final WeComCrypto crypto;
    private final CodeQaService codeQaService;

    public WeComCallbackController(WeComCrypto crypto, CodeQaService codeQaService) {
        this.crypto = crypto;
        this.codeQaService = codeQaService;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String verifyUrl(@RequestParam("msg_signature") String msgSignature,
                            @RequestParam("timestamp") String timestamp,
                            @RequestParam("nonce") String nonce,
                            @RequestParam("echostr") String echoStr) {
        return crypto.decryptEcho(msgSignature, timestamp, nonce, echoStr);
    }

    @PostMapping(consumes = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_XML_VALUE, MediaType.ALL_VALUE},
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String receive(@RequestParam("msg_signature") String msgSignature,
                          @RequestParam("timestamp") String timestamp,
                          @RequestParam("nonce") String nonce,
                          @RequestBody String body) {
        String plainXml = crypto.decryptMessage(msgSignature, timestamp, nonce, body);
        WeComInboundMessage message = WeComInboundMessage.fromXml(plainXml);
        log.info("Received WeCom message type={}, from={}, msgId={}", message.msgType(), message.fromUserName(), message.msgId());
        if ("text".equalsIgnoreCase(message.msgType())) {
            codeQaService.answerAndSend(message);
        }
        return "success";
    }
}
