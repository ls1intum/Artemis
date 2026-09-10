package de.tum.cit.aet.artemis.core.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Utility class for String manipulation
 */
public class StringUtil {

    /** Everything outside ASCII, dropped after the input was decomposed. */
    private static final Pattern NON_ASCII = Pattern.compile("[^\\x00-\\x7F]");

    /** A run of whitespace, which a file name spells as an underscore. */
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    /** The remaining characters a file name may not contain. */
    private static final Pattern UNSAFE_FILENAME_CHARACTER = Pattern.compile("[\\\\/:*?#+%$§\"<>|]");

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
     * Note: the result may be empty (e.g. for an input consisting only of non-ASCII letters such as "テスト"). Callers
     * that use the result as a standalone file or directory name must guard against this to avoid name collisions
     * (see e.g. {@code BaseExercise#getSanitizedExerciseTitle()}).
     *
     * @param input String to sanitize (may be {@code null})
     * @return sanitized, ASCII-only string safe for use in file names, or an empty string if the input is {@code null}
     */
    public static String sanitizeStringForFileName(String input) {
        if (input == null) {
            return "";
        }
        String asciiReduced = NON_ASCII.matcher(Normalizer.normalize(input, Normalizer.Form.NFD)).replaceAll("");
        String underscored = WHITESPACE_RUN.matcher(asciiReduced).replaceAll("_");
        return UNSAFE_FILENAME_CHARACTER.matcher(underscored).replaceAll("");
    }
}
