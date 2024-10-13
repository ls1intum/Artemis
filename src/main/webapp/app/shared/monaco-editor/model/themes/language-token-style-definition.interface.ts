/**
 * Interface for the style of a token in a language.
 * The editor applies these styles to the tokens in the specified language (or all languages), e.g. identifiers, keywords, etc.
 */
export interface LanguageTokenStyleDefinition {
    /**
     * The token to style, e.g. identifier
     */
    token: string;
    /**
     * The language ID for which the token style should be applied.
     * If not specified, the style is applied to all languages.
     */
    languageId?: string;
    /**
     * The color of the text that should be applied to the token.
     */
    foregroundColor?: string;
    /**
     * The background color that should be applied to the token.
     */
    backgroundColor?: string;
    /**
     * The font style that should be applied to the token.
     */
    fontStyle?: 'italic' | 'bold' | 'underline';
}
