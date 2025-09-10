import { LegalDocument, LegalDocumentLanguage, LegalDocumentType } from 'app/core/shared/entities/legal-document.model';

export class PrivacyStatement extends LegalDocument {
    constructor(language: LegalDocumentLanguage) {
        super(LegalDocumentType.PRIVACY_STATEMENT, language);
    }
}
