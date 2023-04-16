export enum LegalDocumentLanguage {
    GERMAN = 'de',
    ENGLISH = 'en',
}

export enum LegalDocumentType {
    PRIVACY_STATEMENT = 'privacy-statement',
    IMPRINT = 'imprint',
}

export class LegalDocument {
    constructor(type: LegalDocumentType, language: LegalDocumentLanguage) {
        this.type = type;
        this.language = language;
    }

    language: LegalDocumentLanguage;
    text: string;
    type: LegalDocumentType;
}
