import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';

export abstract class MonacoEditorInsertAction extends MonacoEditorAction {
    textToInsert: string;

    constructor(id: string, label: string, translationKey: string, icon: IconDefinition | undefined, keybindings: number[] | undefined, textToInsert: string) {
        super(id, label, translationKey, icon, keybindings);
        this.textToInsert = textToInsert;
    }

    run(editor: monaco.editor.ICodeEditor) {
        const selection = editor.getSelection();
        const selectedText = selection ? this.getTextAtRange(editor, selection)?.trim() : undefined;
        if (selection && selectedText !== undefined) {
            this.replaceTextAtRange(editor, selection, this.textToInsert);
        }
        editor.focus();
    }
}
