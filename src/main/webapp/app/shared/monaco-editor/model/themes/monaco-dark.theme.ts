import { MonacoThemeDefinition } from 'app/shared/monaco-editor/model/themes/monaco-theme-definition.interface';
import { MonacoEditorTheme } from 'app/shared/monaco-editor/model/themes/monaco-theme.model';
import { MonacoEditorService } from 'app/shared/monaco-editor/monaco-editor.service';

const themeDefinition: MonacoThemeDefinition = {
    baseTheme: 'vs-dark',
    tokenStyles: [], // TODO
    editorColors: {},
};

export const MONACO_DARK_THEME = new MonacoEditorTheme(MonacoEditorService.DARK_THEME_ID, themeDefinition);
