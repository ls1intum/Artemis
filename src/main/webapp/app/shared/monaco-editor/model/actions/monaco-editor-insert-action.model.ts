import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

export abstract class MonacoEditorInsertAction extends MonacoEditorAction {
    textToInsert: string;

    constructor(id: string, label: string, translationKey: string, keybindings: number[] | undefined, textToInsert: string) {
        super(id, label, translationKey, keybindings);
        this.textToInsert = textToInsert;
    }

    run(editor: monaco.editor.ICodeEditor) {
        const selection = editor.getSelection();
        const selectedText = selection ? this.getTextAtRange(editor, selection)?.trim() : undefined;
        const position = editor.getPosition();
        if (selection && selectedText) {
            this.replaceTextAtRange(editor, selection, this.textToInsert);
        } else if (position) {
            this.insertTextAtPosition(editor, position, this.textToInsert);
        }
    }
}
