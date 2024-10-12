import { MonacoThemeDefinition } from 'app/shared/monaco-editor/model/themes/monaco-theme-definition.interface';
import { MonacoEditorTheme } from 'app/shared/monaco-editor/model/themes/monaco-theme.model';

const themeDefinition: MonacoThemeDefinition = {
    baseTheme: 'vs',
    tokenStyles: [
        {
            token: 'keyword',
            foregroundColor: '#cf222e',
        },
        {
            token: 'comment',
            foregroundColor: '#59636e',
        },
        {
            token: 'string',
            foregroundColor: '#0a3069',
        },
        {
            token: 'number',
            foregroundColor: '#0550ae',
        },
    ],
    editorColors: {
        lineHighlight: {
            borderColor: '#00000000',
            backgroundColor: '#e8e8e8',
        },
        lineNumbers: {
            foregroundColor: '#000000',
            activeForegroundColor: '#000000',
            dimmedForegroundColor: '#000000',
        },
        diff: {
            insertedLineBackgroundColor: '#dafbe1e6',
            insertedTextBackgroundColor: '#aceebbe6',
            removedLineBackgroundColor: '#ffebe9ef',
            removedTextBackgroundColor: '#ff818250',
            diagonalFillColor: '#00000000',
            gutter: {
                insertedLineBackgroundColor: '#d1f8d9',
                removedLineBackgroundColor: '#ffcecb',
            },
        },
    },
};

export const MONACO_LIGHT_THEME = new MonacoEditorTheme('custom-light', themeDefinition);
