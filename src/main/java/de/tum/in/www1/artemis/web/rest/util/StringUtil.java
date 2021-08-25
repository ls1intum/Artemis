package de.tum.in.www1.artemis.web.rest.util;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

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

    /**
     * Strips all whitespaces (spaces, tabs, newlines, etc.) from the input String
     * @param input String to strip
     * @return stripped String
     */
    public static String stripWhitespaces(String input) {
        return input.replaceAll("\\s+", "");
    }

    /**
     * Strips surrounding parentheses, brackets, or braces (first and last characters only)
     * Examples: (X) -> X, [X] -> X, {X} -> X, (X] -> X, [X} -> X, etc.
     * @param input String to strip
     * @return stripped String
     */
    public static String stripSurroundingBrackets(String input) {
        return input.replaceAll("^[(\\[{](.*)[)\\]{]$", "$1");
    }

    public static Optional<List<Integer>> markingStringToList(String marking) {
        try {
            return Optional.of(
                    Arrays.stream(stripSurroundingBrackets(stripWhitespaces(marking)).toLowerCase().split(",")).mapToInt(Integer::parseInt).boxed().collect(Collectors.toList()));
        }
        catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
