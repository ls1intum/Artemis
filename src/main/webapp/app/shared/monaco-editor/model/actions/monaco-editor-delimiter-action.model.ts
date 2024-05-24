import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';

export abstract class MonacoEditorDelimiterAction extends MonacoEditorAction {
    /* Delimiters */
    openDelimiter: string;
    closeDelimiter: string;

    protected constructor(
        id: string,
        label: string,
        translationKey: string,
        icon: IconDefinition | undefined,
        keybindings: number[] | undefined,
        openDelimiter: string,
        closeDelimiter: string,
    ) {
        super(id, label, translationKey, icon, keybindings);
        this.openDelimiter = openDelimiter;
        this.closeDelimiter = closeDelimiter;
    }

    run(editor: monaco.editor.ICodeEditor): void {
        const selection = editor.getSelection();
        const selectedText = selection ? this.getTextAtRange(editor, selection)?.trim() : undefined;
        const position = editor.getPosition();
        if (selection && selectedText) {
            const textToInsert = this.isTextSurroundedByDelimiters(selectedText)
                ? selectedText.slice(this.openDelimiter.length, -this.closeDelimiter.length)
                : `${this.openDelimiter}${selectedText}${this.closeDelimiter}`;
            this.replaceTextAtRange(editor, selection, textToInsert);
        } else if (position) {
            this.insertTextAtPosition(editor, position, `${this.openDelimiter}${this.closeDelimiter}`);
            // Move cursor to the middle of the delimiters
            editor.setPosition(position.delta(0, this.openDelimiter.length));
        }
        editor.focus();
    }

    isTextSurroundedByDelimiters(text: string): boolean {
        return text.startsWith(this.openDelimiter) && text.endsWith(this.closeDelimiter) && text.length >= this.openDelimiter.length + this.closeDelimiter.length;
    }
}
