export enum PrivacyStatementLanguage {
    GERMAN = 'de',
    ENGLISH = 'en',
}

export class PrivacyStatement {
    constructor(defaultLanguage: PrivacyStatementLanguage) {
        this.language = defaultLanguage;
    }

    language: PrivacyStatementLanguage;
    text: string;
}
