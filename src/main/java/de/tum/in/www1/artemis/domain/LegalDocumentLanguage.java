package de.tum.in.www1.artemis.domain;

public enum LegalDocumentLanguage {

    GERMAN, ENGLISH;

    public static LegalDocumentLanguage fromLanguageShortName(String languageShortName) {
        if (("de").equals(languageShortName)) {
            return GERMAN;
        }
        else if (("en").equals(languageShortName)) {
            return ENGLISH;
        }
        else {
            throw new IllegalArgumentException("Language not supported");
        }
    }

    public String getShortName() {
        if (this == GERMAN) {
            return "de";
        }
        else if (this == ENGLISH) {
            return "en";
        }
        else {
            throw new IllegalArgumentException("Language not supported");
        }
    }
}
