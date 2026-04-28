package io.github.bridgewares.codebot.wecom;

public record WeComInboundMessage(
        String toUserName,
        String fromUserName,
        String msgType,
        String content,
        String msgId
) {

    public static WeComInboundMessage fromXml(String xml) {
        return new WeComInboundMessage(
                XmlUtils.getText(xml, "ToUserName"),
                XmlUtils.getText(xml, "FromUserName"),
                XmlUtils.getText(xml, "MsgType"),
                XmlUtils.getText(xml, "Content"),
                XmlUtils.getText(xml, "MsgId")
        );
    }

    public String normalizedQuestion() {
        if (content == null) {
            return "";
        }
        return content.replaceAll("@\\S+", "").trim();
    }
}
