package io.github.bridgewares.codebot.wecom;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

public final class XmlUtils {

    private XmlUtils() {
    }

    public static String getText(String xml, String tagName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList nodes = document.getElementsByTagName(tagName);
            if (nodes.getLength() == 0) {
                return "";
            }
            return nodes.item(0).getTextContent();
        } catch (Exception e) {
            throw new WeComCryptoException("Unable to parse XML tag: " + tagName, e);
        }
    }

    public static String textResponse(String toUser, String fromUser, String content) {
        long now = System.currentTimeMillis() / 1000;
        return "<xml>"
                + "<ToUserName><![CDATA[" + nullToEmpty(toUser) + "]]></ToUserName>"
                + "<FromUserName><![CDATA[" + nullToEmpty(fromUser) + "]]></FromUserName>"
                + "<CreateTime>" + now + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[" + nullToEmpty(content) + "]]></Content>"
                + "</xml>";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
