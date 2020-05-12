/**
 * Languages that can be detected by franc.
 */
export enum FrancLanguage {
    ENGLISH = 'eng',
    GERMAN = 'deu',
    UNDEFINED = 'und',
}

export interface Franc {
    /**
     * Detects language of a given text.
     * @param text whose language should be deteceted
     * @param options
     * @return detected language of type {FrancLanguage} and probability
     */
    all(text: string, options?: { only: FrancLanguage[] }): [FrancLanguage, number];
}
