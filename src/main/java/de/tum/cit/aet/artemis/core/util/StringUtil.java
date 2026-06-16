package de.tum.cit.aet.artemis.core.util;

import java.text.Normalizer;

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
     * Sanitizes a string so it is safe to use as a file or directory name across filesystems and archive tools.
     * <p>
     * It reduces the input to ASCII (decomposing accented letters to their base form, e.g. "ä" -&gt; "a", then dropping
     * any remaining non-ASCII character), collapses whitespace to underscores and removes filesystem-reserved
     * characters. Reducing to ASCII avoids problems with non-UTF-8 mounts, ZIP entry encoding and cross-platform
     * extraction when exercise/exam titles contain international letters. Display contexts (e.g. notifications) must use
     * the raw title instead of this method, so users still see the original characters.
     *
     * @param input String to sanitize
     * @return sanitized, ASCII-only string safe for use in file names
     */
    public static String sanitizeStringForFileName(String input) {
        String asciiReduced = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("[^\\x00-\\x7F]", "");
        return asciiReduced.replaceAll("\\s+", "_").replaceAll("[\\\\/:*?#+%$§\"<>|]", "");
    }
}
