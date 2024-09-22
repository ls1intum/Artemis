import { NgModule } from '@angular/core';
import { CUSTOM_MARKDOWN_CONFIG, CUSTOM_MARKDOWN_LANGUAGE, CUSTOM_MARKDOWN_LANGUAGE_ID } from 'app/shared/monaco-editor/model/languages/monaco-custom-markdown.language';

import * as monaco from 'monaco-editor';

monaco.languages.register({ id: CUSTOM_MARKDOWN_LANGUAGE_ID });
monaco.languages.setLanguageConfiguration(CUSTOM_MARKDOWN_LANGUAGE_ID, CUSTOM_MARKDOWN_CONFIG);
monaco.languages.setMonarchTokensProvider(CUSTOM_MARKDOWN_LANGUAGE_ID, CUSTOM_MARKDOWN_LANGUAGE);

@NgModule({
    declarations: [],
    exports: [],
})
export class MonacoEditorModule {}
