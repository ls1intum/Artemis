import { NgModule } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/monaco-diff-editor.component';
import { conf, language } from 'app/shared/monaco-editor/model/languages/monaco-custom-markdown.language';

import * as monaco from 'monaco-editor';

monaco.editor.defineTheme('custom-dark', {
    base: 'vs-dark',
    inherit: true,
    rules: [
        {
            token: 'task',
            foreground: '#67f0c9',
            fontStyle: 'bold',
        },
    ],
    colors: {},
});

alert('register!');
monaco.languages.register({ id: 'custom-md' });
monaco.languages.setLanguageConfiguration('custom-md', conf);
monaco.languages.setMonarchTokensProvider('custom-md', language);

@NgModule({
    declarations: [MonacoEditorComponent, MonacoDiffEditorComponent],
    exports: [MonacoEditorComponent, MonacoDiffEditorComponent],
})
export class MonacoEditorModule {}
