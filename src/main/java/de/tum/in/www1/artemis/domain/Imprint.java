package de.tum.in.www1.artemis.domain;

public class Imprint extends LegalDocument {

    public Imprint(LegalDocumentLanguage language) {
        super(LegalDocumentType.IMPRINT, language);
    }

    public Imprint(String imprintText, LegalDocumentLanguage language) {
        super(LegalDocumentType.IMPRINT, imprintText, language);
    }
}
