package de.tum.in.www1.artemis.web.rest.util;

import org.apache.commons.lang3.StringUtils;

/**
 * Utility class for String manipulation
 */
public class StringUtil {

    public static String ILLEGAL_CHARACTERS = "#%&{}\\<>*?/$!'\":@+`|=";

    /**
     * Removes all chars from ILLEGAL_CHARACTERS from the input String
     * @param input String to strip
     * @return stripped String
     */
    public static String stripIllegalCharacters(String input) {
        return StringUtils.replaceChars(input, ILLEGAL_CHARACTERS, null);
    }
}
