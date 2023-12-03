package de.tum.in.www1.artemis.service.connectors.localci.scaParser.strategy;

import java.io.File;

import org.w3c.dom.Element;

/**
 * Utility class providing shared functionality for report parsing
 */
final class ParserUtils {

    private ParserUtils() {
    }

    /**
     * Extracts and parses an attribute to an int. Defaults to 0 if parsing fails.
     *
     * @param element   Element with attributes
     * @param attribute Attribute to extract
     * @return extracted number
     */
    public static int extractInt(Element element, String attribute) {
        try {
            return Integer.parseInt(element.getAttribute(attribute));
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Transforms operating system dependent file separators in a path to unix file separators
     *
     * @param path String representation of a path to be transformed
     * @return path with unix file separators
     */
    public static String transformToUnixPath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        return path.replace(File.separator, "/");
    }

    /**
     * Strips new lines and trailing or leading whitespaces in a String
     *
     * @param text to strip
     * @return striped text
     */
    public static String stripNewLinesAndWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replaceAll("([\\r\\n])", "").trim();
    }
}
