package de.tum.cit.aet.artemis.exercise.service;

/**
 * Minimal HTML escaping helpers used by the problem-statement rendering pipeline.
 * Only covers the subset of characters that matter for attribute values and text content
 * produced in the server-side renderer; not a general-purpose HTML library.
 */
public final class HtmlEscaper {

    private HtmlEscaper() {
    }

    /**
     * Escapes a value intended for use inside an HTML attribute delimited by double quotes.
     * Escapes the five characters that can break out of or terminate the attribute context.
     *
     * @param value the raw value
     * @return the value with {@code &}, {@code "}, {@code <} and {@code >} replaced by their HTML entities
     */
    public static String escapeAttribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Escapes a value intended for use as HTML text content.
     * Escapes only the characters that can open HTML tags or entities.
     *
     * @param value the raw value
     * @return the escaped value
     */
    public static String escapeText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
