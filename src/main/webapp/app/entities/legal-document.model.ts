export enum LegalDocumentLanguage {
    GERMAN = 'de',
    ENGLISH = 'en',
}

export enum LegalDocumentType {
    PRIVACY_STATEMENT = 'privacy-statement',
    IMPRINT = 'imprint',
}

export abstract class LegalDocument {
    protected constructor(type: LegalDocumentType, language: LegalDocumentLanguage) {
        this.language = language;
    }

    language: LegalDocumentLanguage;
    text: string;
}
