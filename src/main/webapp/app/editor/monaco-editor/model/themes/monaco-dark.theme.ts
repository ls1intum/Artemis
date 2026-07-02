import { MonacoThemeDefinition } from 'app/editor/monaco-editor/model/themes/monaco-theme-definition.interface';

export const MONACO_DARK_THEME_DEFINITION: MonacoThemeDefinition = {
    id: 'custom-dark',
    baseTheme: 'vs-dark',
    tokenStyles: [
        {
            token: 'keyword',
            foregroundColor: '#ff7b72',
        },
        {
            token: 'comment',
            foregroundColor: '#9198a1',
        },
        {
            token: 'string',
            foregroundColor: '#a5d6ff',
        },
        {
            token: 'number',
            foregroundColor: '#79c0ff',
        },
    ],
    editorColors: {
        // Tracks --module-bg (the dark slate panel surface) so the editor blends into its container.
        // Monaco themes require literal hex (no CSS vars), so keep this in sync with $neutral-dark.
        backgroundColor: '#16191d',
        lineHighlight: {
            borderColor: '#00000000',
            backgroundColor: '#262b31', // active-line highlight: the slate "raised" step (surface-800)
        },
        lineNumbers: {
            foregroundColor: '#ffffff',
            activeForegroundColor: '#ffffff',
            dimmedForegroundColor: '#ffffff',
        },
        diff: {
            insertedLineBackgroundColor: '#2ea04326',
            insertedTextBackgroundColor: '#2ea04326',
            removedLineBackgroundColor: '#f8514926',
            removedTextBackgroundColor: '#f8514946',
            diagonalFillColor: '#00000000',
            gutter: {
                insertedLineBackgroundColor: '#3fb9504d',
                removedLineBackgroundColor: '#f851494d',
            },
        },
    },
};
