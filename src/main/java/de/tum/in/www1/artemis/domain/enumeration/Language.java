package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The Language enumeration.
 */
public enum Language {

    ENGLISH, GERMAN;

    public String getLanguageKey() {
        switch (this) {
            case ENGLISH -> {
                return "en";
            }
            case GERMAN -> {
                return "de";
            }
            default -> {
                return "invalid_key";
            }
        }
    }
}
