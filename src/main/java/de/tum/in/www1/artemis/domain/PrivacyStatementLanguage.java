package de.tum.in.www1.artemis.domain;

public enum PrivacyStatementLanguage {

    GERMAN, ENGLISH;

    /**
     * Returns the PrivacyStatementLanguage for the given language short name.
     *
     * @param languageShortName the short name of the language
     * @return the PrivacyStatementLanguage for the given language short name
     */
    public static PrivacyStatementLanguage fromLanguageShortName(String languageShortName) {
        return switch (languageShortName.toLowerCase()) {
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
