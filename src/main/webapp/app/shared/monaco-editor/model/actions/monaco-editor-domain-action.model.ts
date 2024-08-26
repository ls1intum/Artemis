import { MonacoEditorAction } from './monaco-editor-action.model';
import * as monaco from 'monaco-editor';

/**
 * Class representing domain actions for Artemis-specific use cases.
 */
export abstract class MonacoEditorDomainAction extends MonacoEditorAction {
    abstract getOpeningIdentifier(): string;

    /**
     * Inserts, below the current line, a new line with the domain action identifier and the given text.
     * Afterward, if specified, sets the selection to cover exactly the provided text part.
     * @param editor The editor in which to insert the text
     * @param text The text to insert. Can be empty.
     * @param indent Whether to indent the inserted text with a tab.
     * @param updateSelection Whether to update the selection after inserting the text
     */
    addTextWithDomainActionIdentifier(editor: monaco.editor.ICodeEditor, text: string, indent = false, updateSelection = true): void {
        this.clearSelection(editor);
        this.moveCursorToEndOfLine(editor);
        const identifierWithText = text ? `${this.getOpeningIdentifier()} ${text}` : this.getOpeningIdentifier();
        const insertText = indent ? `\n\t${identifierWithText}` : `\n${identifierWithText}`;
        this.insertTextAtPosition(editor, this.getPosition(editor), insertText);
        if (updateSelection) {
            const newPosition = this.getPosition(editor);
            // Set the selection to cover exactly the text part
            this.setSelection(editor, {
                startLineNumber: newPosition.lineNumber,
                endLineNumber: newPosition.lineNumber,
                startColumn: newPosition.column - text.length,
                endColumn: newPosition.column,
            });
        }
        editor.focus();
    }
}
