import * as monaco from 'monaco-editor';

export class MonacoEditorTheme {
    constructor(
        private readonly name: string,
        private readonly themeDefinition: MonacoEditorThemeDefinition,
    ) {}

    register(): void {
        const colors = this.themeDefinition.colors;
        const rawColors: Record<string, string | undefined> = {
            'editor.lineHighlightBackground': colors.activeLineHighlightBackground,
            'editor.lineHighlightBorder': colors.activeLineHighlightBorder,
            'diffEditor.insertedTextBackground': colors.insertedTextBackground,
            'diffEditor.removedTextBackground': colors.removedTextBackground,
            'diffEditor.insertedLineBackground': colors.insertedLineBackground,
            'diffEditor.removedLineBackground': colors.removedLineBackground,
            'diffEditor.diagonalFill': colors.diagonalFill,
            'diffEditorGutter.insertedLineBackground': colors.gutterInsertedLineBackground,
            'diffEditorGutter.removedLineBackground': colors.gutterRemovedLineBackground,
        };

        // Define a second object that contains only defined colors
        const definedColors: Record<string, string> = {};
        for (const key in rawColors) {
            if (rawColors[key]) {
                definedColors[key] = rawColors[key]!;
            }
        }

        /*monaco.editor.defineTheme(this.name, {
            base: this.themeDefinition.base,
            inherit: this.themeDefinition.inherit,
            rules: [],
            colors: definedColors,
        });*/
    }

    apply(): void {
        monaco.editor.setTheme(this.name);
    }
}

export const CUSTOM_DARK = new MonacoEditorTheme('custom-dark', {
    base: 'vs-dark',
    inherit: true,
    colors: {
        insertedTextBackground: '#2ea04340',
        removedTextBackground: '#f8514940',
        insertedLineBackground: '#3fb95010',
        removedLineBackground: '#f8514910',
        gutterInsertedLineBackground: '#2ea0437d',
        gutterRemovedLineBackground: '#f851497d',
        diagonalFill: '#00000000',
        activeLineHighlightBorder: '#00000000',
        activeLineHighlightBackground: '#282a2e',
    },
});

export interface MonacoEditorThemeDefinition {
    base: 'vs' | 'vs-dark';
    inherit: boolean;
    colors: MonacoEditorThemeColors;
}

interface MonacoEditorThemeColors {
    /**
     * Background color for the text inserted on a line in the diff editor. Note that this will overlap with the `insertedLineBackground`.
     */
    insertedTextBackground?: string;
    /**
     * Background color for the text removed on a line in the diff editor. Note that this will overlap with the `removedLineBackground`.
     */
    removedTextBackground?: string;
    /**
     * Background color for an inserted line in the diff editor.
     */
    insertedLineBackground?: string;
    /**
     * Background color for a removed line in the diff editor.
     */
    removedLineBackground?: string;
    /**
     * Background color for the gutter in the diff editor on an inserted line.
     */
    gutterInsertedLineBackground?: string;
    /**
     * Background color for the gutter in the diff editor on a removed line.
     */
    gutterRemovedLineBackground?: string;
    /**
     * Color of the diagonal fill (zigzag pattern) in the diff editor.
     */
    diagonalFill?: string;
    /**
     * Color of the border around the currently active line in the Monaco editor.
     */
    activeLineHighlightBorder?: string;
    /**
     * Background color for the currently active line in the Monaco editor.
     */
    activeLineHighlightBackground?: string;
}
