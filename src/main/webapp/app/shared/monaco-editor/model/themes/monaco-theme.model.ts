import { MonacoThemeDefinition } from 'app/shared/monaco-editor/model/themes/monaco-theme-definition.interface';
import * as monaco from 'monaco-editor';

export class MonacoEditorTheme {
    constructor(
        private readonly id: string,
        private readonly themeDefinition: MonacoThemeDefinition,
    ) {}

    getId(): string {
        return this.id;
    }

    private stripUndefinedValues(obj: Record<string, string | undefined>): Record<string, string> {
        const result: Record<string, string> = {};
        for (const [key, value] of Object.entries(obj)) {
            if (value !== undefined) {
                result[key] = value;
            }
        }
        return result;
    }

    register(): void {
        const colorDefinitions = this.themeDefinition.editorColors;
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

        const ruleDefinitions = this.themeDefinition.tokenStyles;
        const rules = ruleDefinitions.map((rule) => {
            return {
                token: `${rule.token}${rule.languageId ? '.' + rule.languageId : ''}`,
                foreground: rule.foregroundColor,
                background: rule.backgroundColor,
                fontStyle: rule.fontStyle,
            };
        });

        monaco.editor.defineTheme(this.id, {
            base: this.themeDefinition.baseTheme,
            inherit: true,
            rules: rules,
            colors: this.stripUndefinedValues(colors),
        });
    }
}
