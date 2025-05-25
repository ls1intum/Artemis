import { LegalDocument, LegalDocumentLanguage, LegalDocumentType } from 'app/core/shared/entities/legal-document.model';

export class Imprint extends LegalDocument {
    constructor(language: LegalDocumentLanguage) {
        super(LegalDocumentType.IMPRINT, language);
    }
}
