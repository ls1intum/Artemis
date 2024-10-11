import { LanguageTokenStyleDefinition } from 'app/shared/monaco-editor/model/themes/language-token-style-definition.interface';
import { EditorColors } from 'app/shared/monaco-editor/model/themes/editor-colors.interface';

export interface MonacoThemeDefinition {
    baseTheme: 'vs' | 'vs-dark' | 'hc-light' | 'hc-black';
    tokenStyles: LanguageTokenStyleDefinition[];
    editorColors: EditorColors;
}
