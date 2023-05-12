/**
 * An enum representing the languages that are used for legal documents (privacy statement, imprint)
 */
export enum LegalDocumentLanguage {
    GERMAN = 'de',
    ENGLISH = 'en',
}

/**
 * An enum representing the types of legal documents (privacy statement, imprint)
 */
export enum LegalDocumentType {
    PRIVACY_STATEMENT = 'privacy-statement',
    IMPRINT = 'imprint',
}

/**
 * A class representing a legal document (privacy statement, imprint)
 */
export class LegalDocument {
    language: LegalDocumentLanguage;
    text: string;
    type: LegalDocumentType;
    constructor(type: LegalDocumentType, language: LegalDocumentLanguage) {
        this.type = type;
        this.language = language;
    }
}
