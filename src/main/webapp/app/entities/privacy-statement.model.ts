export enum PrivacyStatementLanguage {
    GERMAN = 'de',
    ENGLISH = 'en',
}

export class PrivacyStatement {
    language: PrivacyStatementLanguage;
    text: string;
}
