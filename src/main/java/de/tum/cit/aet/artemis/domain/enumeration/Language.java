package de.tum.cit.aet.artemis.domain.enumeration;

/**
 * The Language enumeration.
 */
public enum Language {

    ENGLISH("en"), GERMAN("de");

    private final String shortName;

    Language(String shortName) {
        this.shortName = shortName;
    }

    /**
     * Returns the language of for a given language short name.
     *
     * @param languageShortName the short name of the language
     * @return the language that matches the given short name
     */
    public static Language fromLanguageShortName(String languageShortName) {
        for (Language language : Language.values()) {
            if (language.getShortName().equals(languageShortName)) {
                return language;
            }
        }
        throw new IllegalArgumentException("Language not supported");
    }

    /**
     * Checks if the given language short name is valid.
     *
     * @param languageShortName the short name of the language
     * @return true if the short name is valid, false otherwise
     */
    public static boolean isValidShortName(String languageShortName) {
        for (Language language : Language.values()) {
            if (language.getShortName().equals(languageShortName)) {
                return true;
            }
        }
        return false;
    }

    public String getShortName() {
        return this.shortName;
    }
}
