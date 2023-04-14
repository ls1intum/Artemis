package de.tum.in.www1.artemis.domain;

public enum PrivacyStatementLanguage {

    GERMAN, ENGLISH;

    public static PrivacyStatementLanguage fromLanguageShortName(String languageShortName) {
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
