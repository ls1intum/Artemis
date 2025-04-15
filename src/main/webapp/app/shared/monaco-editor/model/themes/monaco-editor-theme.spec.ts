import * as monaco from 'monaco-editor';
import { EditorColors } from 'app/shared/monaco-editor/model/themes/editor-colors.interface';
import { LanguageTokenStyleDefinition } from 'app/shared/monaco-editor/model/themes/language-token-style-definition.interface';
import { MonacoThemeDefinition } from 'app/shared/monaco-editor/model/themes/monaco-theme-definition.interface';
import { MonacoEditorTheme } from 'app/shared/monaco-editor/model/themes/monaco-editor-theme.model';

describe('MonacoEditorTheme', () => {
    const colorDefinitions: EditorColors = {
        backgroundColor: '#181a18',
        foregroundColor: '#ffffff',
        diff: {
            insertedLineBackgroundColor: '#2ea04326',
            insertedTextBackgroundColor: '#2ea04326',
            removedLineBackgroundColor: undefined, // Explicit undefined to test that it is removed before being passed to Monaco
            removedTextBackgroundColor: undefined,
        },
    };

    const tokenStyleDefinitions: LanguageTokenStyleDefinition[] = [
        {
            token: 'keyword',
            foregroundColor: '#ff7b72',
        },
        {
            token: 'keyword',
            languageId: 'custom-language-id',
            foregroundColor: '#ffffff',
        },
    ];

    const themeDefinition: MonacoThemeDefinition = {
        id: 'test-theme',
        baseTheme: 'vs',
        tokenStyles: tokenStyleDefinitions,
        editorColors: colorDefinitions,
    };

    it('should correctly register a theme', () => {
        const theme = new MonacoEditorTheme(themeDefinition);
        const defineThemeSpy = jest.spyOn(monaco.editor, 'defineTheme');
        theme.register();
        expect(defineThemeSpy).toHaveBeenCalledExactlyOnceWith('test-theme', {
            base: 'vs',
            inherit: true,
            rules: [
                {
                    token: 'keyword',
                    foreground: '#ff7b72',
                    background: undefined,
                    fontStyle: undefined,
                },
                {
                    token: 'keyword.custom-language-id',
                    foreground: '#ffffff',
                    background: undefined,
                    fontStyle: undefined,
                },
            ],
            colors: {
                'editor.background': '#181a18',
                'editor.foreground': '#ffffff',
                'diffEditor.insertedLineBackground': '#2ea04326',
                'diffEditor.insertedTextBackground': '#2ea04326',
            },
        });
    });
});
