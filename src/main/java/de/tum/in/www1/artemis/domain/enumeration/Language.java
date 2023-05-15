package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The Language enumeration.
 */
public enum Language {

    ENGLISH, GERMAN;

    /**
     * Returns the language of for a given language short name.
     *
     * @param languageShortName the short name of the language
     * @return the language that matches the given short name
     */
    public static Language fromLanguageShortName(String languageShortName) {
        return switch (languageShortName) {
            case "de" -> GERMAN;
            case "en" -> ENGLISH;
            default -> throw new IllegalArgumentException("Language not supported");
        };
    }

    public static boolean isValidShortName(String language) {
        return switch (language.toLowerCase()) {
            case "de", "en" -> true;
            default -> false;
        };
    }

    public String getShortName() {
        return switch (this) {
            case GERMAN -> "de";
            case ENGLISH -> "en";
        };
    }
}
