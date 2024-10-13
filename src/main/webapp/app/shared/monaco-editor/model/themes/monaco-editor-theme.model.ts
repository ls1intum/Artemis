import { MonacoThemeDefinition } from 'app/shared/monaco-editor/model/themes/monaco-theme-definition.interface';
import * as monaco from 'monaco-editor';

export class MonacoEditorTheme {
    constructor(private readonly themeDefinition: MonacoThemeDefinition) {}

    getId(): string {
        return this.themeDefinition.id;
    }

    /**
     * Creates a new record without any entries that have a value of `undefined`.
     * @param record The record whose keys to filter.
     * @returns The new record, only containing keys with defined values.
     * @private
     */
    private getRecordWithoutUndefinedEntries(record: Record<string, string | undefined>): Record<string, string> {
        const result: Record<string, string> = {};
        for (const [key, value] of Object.entries(record)) {
            if (value !== undefined) {
                result[key] = value;
            }
        }
        return result;
    }

    register(): void {
        const colorDefinitions = this.themeDefinition.editorColors;
        // The color keys are available here: https://code.visualstudio.com/api/references/theme-color
        const colors = {
            'editor.background': colorDefinitions.backgroundColor,
            'editor.foreground': colorDefinitions.foregroundColor,
            'editorLineNumber.foreground': colorDefinitions.lineNumbers?.foregroundColor,
            'editorLineNumber.activeForeground': colorDefinitions.lineNumbers?.activeForegroundColor,
            'editorLineNumber.dimmedForeground': colorDefinitions.lineNumbers?.dimmedForegroundColor,
            'editor.lineHighlightBackground': colorDefinitions.lineHighlight?.backgroundColor,
            'editor.lineHighlightBorder': colorDefinitions.lineHighlight?.borderColor,
            'diffEditor.insertedLineBackground': colorDefinitions.diff?.insertedLineBackgroundColor,
            'diffEditor.insertedTextBackground': colorDefinitions.diff?.insertedTextBackgroundColor,
            'diffEditor.removedTextBackground': colorDefinitions.diff?.removedTextBackgroundColor,
            'diffEditor.removedLineBackground': colorDefinitions.diff?.removedLineBackgroundColor,
            'diffEditor.diagonalFill': colorDefinitions.diff?.diagonalFillColor,
            'diffEditorGutter.insertedLineBackground': colorDefinitions.diff?.gutter?.insertedLineBackgroundColor,
            'diffEditorGutter.removedLineBackground': colorDefinitions.diff?.gutter?.removedLineBackgroundColor,
        };

        const tokenStyleDefinitions = this.themeDefinition.tokenStyles;
        const rules = tokenStyleDefinitions.map((tokenDefinition) => {
            // Language-specific tokens have the key `token.languageId`, e.g. keyword.custom-md
            return {
                token: `${tokenDefinition.token}${tokenDefinition.languageId ? '.' + tokenDefinition.languageId : ''}`,
                foreground: tokenDefinition.foregroundColor,
                background: tokenDefinition.backgroundColor,
                fontStyle: tokenDefinition.fontStyle,
            };
        });

        // We cannot pass undefined colors to Monaco, so we filter them out to preserve the default values.
        monaco.editor.defineTheme(this.getId(), {
            base: this.themeDefinition.baseTheme,
            inherit: true,
            rules: rules,
            colors: this.getRecordWithoutUndefinedEntries(colors),
        });
    }
}
