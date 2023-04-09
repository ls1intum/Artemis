package de.tum.in.www1.artemis.domain;

public class LegalDocument {

    private final LegalDocumentType type;

    private String text;

    private final LegalDocumentLanguage language;

    public LegalDocument(LegalDocumentType type, LegalDocumentLanguage language) {
        this.type = type;
        this.language = language;
    }

    public LegalDocument(LegalDocumentType type, String text, LegalDocumentLanguage language) {
        this.type = type;
        this.text = text;
        this.language = language;
    }

    public void setText(String text) {
        this.text = text;
    }

    public LegalDocumentType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public LegalDocumentLanguage getLanguage() {
        return language;
    }
}
