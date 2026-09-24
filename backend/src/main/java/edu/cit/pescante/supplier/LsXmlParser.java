package edu.cit.pescante.supplier;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

/**
 * Package-private minimal XML parsing utility.
 * Uses standard javax.xml (always available in Java SE) — no JAXB required.
 */
class LsXmlParser {

    private LsXmlParser() {}

    /**
     * Extracts the text content of the first element matching {@code tag}.
     * Returns null if the tag is not found or the XML is malformed.
     */
    static String extractTag(String xml, String tag) {
        if (xml == null || xml.isBlank()) return null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            NodeList nodes = doc.getElementsByTagName(tag);
            if (nodes.getLength() > 0) {
                return nodes.item(0).getTextContent().trim();
            }
        } catch (Exception ignored) {
            // Not parseable – caller handles null
        }
        return null;
    }

    /**
     * Extracts the numeric status code from a PurchaseOrderStatus XML body.
     * Returns -1 if not present.
     */
    static int extractStatusCode(String xml) {
        String raw = extractTag(xml, "StatusCode");
        if (raw == null) return -1;
        try { return Integer.parseInt(raw); } catch (NumberFormatException e) { return -1; }
    }

    /**
     * Extracts the LegacySupply error code string (e.g. "E-AUTH-07") from
     * an error response body.
     */
    static String extractErrorCode(String xml) {
        return extractTag(xml, "Code");
    }

    /**
     * Returns true if the XML body contains the given LegacySupply error code.
     */
    static boolean hasErrorCode(String xml, String code) {
        return code != null && code.equals(extractErrorCode(xml));
    }
}
