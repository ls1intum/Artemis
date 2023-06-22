package de.tum.in.www1.artemis.domain;

import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.LegalDocumentType;

public class PrivacyStatement extends LegalDocument {

    public PrivacyStatement(Language language) {
        super(LegalDocumentType.PRIVACY_STATEMENT, language);
    }

    public PrivacyStatement(String privacyStatementText, Language language) {
        super(LegalDocumentType.PRIVACY_STATEMENT, privacyStatementText, language);
    }
}
