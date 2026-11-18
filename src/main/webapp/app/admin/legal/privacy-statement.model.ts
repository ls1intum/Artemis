import { LegalDocument, LegalDocumentLanguage, LegalDocumentType } from 'app/admin/legal/legal-document.model';

export class PrivacyStatement extends LegalDocument {
    constructor(language: LegalDocumentLanguage) {
        super(LegalDocumentType.PRIVACY_STATEMENT, language);
    }
}
