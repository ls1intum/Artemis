import { MonacoThemeDefinition } from 'app/shared/monaco-editor/model/themes/monaco-theme-definition.interface';
import { MonacoEditorTheme } from 'app/shared/monaco-editor/model/themes/monaco-theme.model';

const themeDefinition: MonacoThemeDefinition = {
    baseTheme: 'vs-dark',
    tokenStyles: [
        /*{
            token: 'keyword',
            languageId: CUSTOM_MARKDOWN_LANGUAGE_ID,
            foregroundColor: '#ffffff',
        },*/
        {
            token: 'keyword',
            foregroundColor: '#ff7b72',
        },
        {
            token: 'comment',
            foregroundColor: '#9198a1',
        },
    ], // TODO
    editorColors: {
        backgroundColor: '#181a18',
        lineHighlight: {
            borderColor: '#00000000', // TODO bg color
        },
        diff: {
            insertedLineBackgroundColor: '#2ea04326',
            insertedTextBackgroundColor: '#2ea04366',
            removedLineBackgroundColor: '#f8514926',
            removedTextBackgroundColor: '#f8514966',
            diagonalFillColor: '#00000000',
            gutter: {
                insertedLineBackgroundColor: '#3fb9504d',
                removedLineBackgroundColor: '#f851494d',
            },
        },
    },
};

export const MONACO_DARK_THEME = new MonacoEditorTheme('custom-dark', themeDefinition);
