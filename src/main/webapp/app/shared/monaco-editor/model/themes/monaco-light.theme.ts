import { MonacoThemeDefinition } from 'app/shared/monaco-editor/model/themes/monaco-theme-definition.interface';
import { MonacoEditorTheme } from 'app/shared/monaco-editor/model/themes/monaco-theme.model';

const themeDefinition: MonacoThemeDefinition = {
    baseTheme: 'vs',
    tokenStyles: [], // TODO
    editorColors: {},
};

export const MONACO_LIGHT_THEME = new MonacoEditorTheme('custom-light', themeDefinition);
