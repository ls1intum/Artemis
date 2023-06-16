package de.tum.in.www1.artemis.domain;

import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.LegalDocumentType;

public class LegalDocument {

    private final LegalDocumentType type;

    private String text;

    private final Language language;

    public LegalDocument(LegalDocumentType type, Language language) {
        this.type = type;
        this.language = language;
    }

    public LegalDocument(LegalDocumentType type, String text, Language language) {
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

    public Language getLanguage() {
        return language;
    }
}
