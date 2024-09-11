package de.tum.cit.aet.artemis.web.rest.util;

import org.apache.commons.lang3.StringUtils;

/**
 * Utility class for String manipulation
 */
public class StringUtil {

    public static final String ILLEGAL_CHARACTERS = "#%&{}\\<>*?/$!'\":@+`|=.";

    /**
     * Removes all chars from ILLEGAL_CHARACTERS from the input String
     *
     * @param input String to strip
     * @return stripped String
     */
    public static String stripIllegalCharacters(String input) {
        return StringUtils.replaceChars(input, ILLEGAL_CHARACTERS, null);
    }

    /**
     * Replaces whitespace with underscores and removes illegal characters, which allows to use the string in file names
     *
     * @param input String to sanitize
     * @return sanitized string
     */
    public static String sanitizeStringForFileName(String input) {
        return input.replaceAll("\\s+", "_").replaceAll("[\\\\/:*?#+%$§\"<>|]", "");
    }
}
