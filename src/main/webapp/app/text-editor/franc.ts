export enum FrancLanguage {
    ENGLISH = 'eng',
    GERMAN = 'deu',
    UNDEFINED = 'und',
}

export const SUPPORTED_TEXT_LANGUAGES = Object.keys(FrancLanguage).map(key => FrancLanguage[key]);

export interface Franc {
    all(text: string, options?: { only: FrancLanguage[] }): [FrancLanguage, number];
}
