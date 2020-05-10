export enum FrancLanguage {
    ENGLISH = 'eng',
    GERMAN = 'deu',
    UNDEFINED = 'und',
}

export interface Franc {
    all(text: string, options?: { only: FrancLanguage[] }): [FrancLanguage, number];
}
