package de.tum.in.www1.artemis.domain;

public class PrivacyStatement extends LegalDocument {

    public PrivacyStatement(LegalDocumentLanguage language) {
        super(LegalDocumentType.PRIVACY_STATEMENT, language);
    }

    public PrivacyStatement(String privacyStatementText, LegalDocumentLanguage language) {
        super(LegalDocumentType.PRIVACY_STATEMENT, privacyStatementText, language);
    }
}
