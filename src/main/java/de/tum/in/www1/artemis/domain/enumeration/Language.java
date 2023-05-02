package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The Language enumeration.
 */
public enum Language {

    ENGLISH, GERMAN;

    /**
     * @return the language key corresponding to the current case
     */
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
