import { NgModule } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/monaco-diff-editor.component';
import { CUSTOM_MARKDOWN_CONFIG, CUSTOM_MARKDOWN_LANGUAGE, CUSTOM_MARKDOWN_LANGUAGE_ID } from 'app/shared/monaco-editor/model/languages/monaco-custom-markdown.language';

import * as monaco from 'monaco-editor';

monaco.languages.register({ id: CUSTOM_MARKDOWN_LANGUAGE_ID });
monaco.languages.setLanguageConfiguration(CUSTOM_MARKDOWN_LANGUAGE_ID, CUSTOM_MARKDOWN_CONFIG);
monaco.languages.setMonarchTokensProvider(CUSTOM_MARKDOWN_LANGUAGE_ID, CUSTOM_MARKDOWN_LANGUAGE);

@NgModule({
    declarations: [MonacoEditorComponent, MonacoDiffEditorComponent],
    exports: [MonacoEditorComponent, MonacoDiffEditorComponent],
})
export class MonacoEditorModule {}
