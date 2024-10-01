import { Injectable } from '@angular/core';
import * as monaco from 'monaco-editor';
import { CUSTOM_MARKDOWN_CONFIG, CUSTOM_MARKDOWN_LANGUAGE, CUSTOM_MARKDOWN_LANGUAGE_ID } from 'app/shared/monaco-editor/model/languages/monaco-custom-markdown.language';
import { Theme } from 'app/core/theme/theme.service';

@Injectable({ providedIn: 'root' })
export class MonacoEditorService {
    constructor() {
        monaco.languages.register({ id: CUSTOM_MARKDOWN_LANGUAGE_ID });
        monaco.languages.setLanguageConfiguration(CUSTOM_MARKDOWN_LANGUAGE_ID, CUSTOM_MARKDOWN_CONFIG);
        monaco.languages.setMonarchTokensProvider(CUSTOM_MARKDOWN_LANGUAGE_ID, CUSTOM_MARKDOWN_LANGUAGE);
    }

    applyTheme(artemisTheme: Theme): void {
        monaco.editor.setTheme(artemisTheme === Theme.LIGHT ? 'vs' : 'vs-dark');
    }
}
