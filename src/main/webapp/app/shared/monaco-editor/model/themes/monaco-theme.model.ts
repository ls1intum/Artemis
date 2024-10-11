import { MonacoThemeDefinition } from 'app/shared/monaco-editor/model/themes/monaco-theme-definition.interface';
import * as monaco from 'monaco-editor';

export class MonacoEditorTheme {
    constructor(
        private readonly id: string,
        private readonly themeDefinition: MonacoThemeDefinition,
    ) {}

    register(): void {
        monaco.editor.defineTheme(this.id, {
            base: this.themeDefinition.baseTheme,
            inherit: true,
            rules: [],
            colors: {},
        });
    }
}
