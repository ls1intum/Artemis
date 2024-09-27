package de.tum.cit.aet.artemis.shared.util;

public class StringUtils {

    private StringUtils() {
        // Utility class
    }

    /**
     * Checks if a given word is likely singular.
     * <p>
     * This method is a simple heuristic to determine if a word is likely singular.
     * It checks for common plural endings and some exceptions.
     * </p>
     *
     * @param word the word to check
     * @return {@code true} if the word is likely singular, {@code false} otherwise
     * @throws IllegalArgumentException if the input word is {@code null} or empty
     */
    public static boolean isSingular(String word) {
        // Null or empty check
        if (word == null || word.isEmpty()) {
            throw new IllegalArgumentException("Input word cannot be null or empty");
        }

        // Convert word to lower case to make the comparison case-insensitive
        String lowerWord = word.toLowerCase();

        // Check for common plural endings
        if (lowerWord.endsWith("s")) {
            // Words ending in "s" could be plural, but some singular words also end in "s"
            // To rule out false positives, check for some exceptions

            // Exception examples
            if (lowerWord.endsWith("ss") || lowerWord.endsWith("is")) {
                return true;  // "glass", "analysis" are singular
            }
            return false;  // Likely plural, ends in 's'
        }

        // For words ending in "es"
        if (lowerWord.endsWith("es")) {
            return false;  // Likely plural, e.g., "boxes", "wishes"
        }

        // Consider singular if it doesn't match common plural suffixes
        return true;
    }
}
