import { MonacoEditorAction } from './monaco-editor-action.model';
import * as monaco from 'monaco-editor';

/**
 * Class representing domain actions for Artemis-specific use cases.
 * TODO: In the future, each domain action should have its own logic and a unique identifier (e.g. multiple choice questions, drag and drop questions).
 */
export abstract class MonacoEditorDomainAction extends MonacoEditorAction {
    abstract getOpeningIdentifier(): string;

    /**
     * Inserts, below the current line, a new line with the domain action identifier and the given text.
     * Afterward, sets the selection to cover exactly the provided text part.
     * @param editor The editor in which to insert the text
     * @param text The text to insert
     * @param indent Whether to indent the inserted text with a tab.
     */
    addTextWithDomainActionIdentifier(editor: monaco.editor.ICodeEditor, text: string, indent: boolean = false): void {
        this.clearSelection(editor);
        this.moveCursorToEndOfLine(editor);
        const insertText = indent ? `\n\t${this.getOpeningIdentifier()} ${text}` : `\n${this.getOpeningIdentifier()} ${text}`;
        this.insertTextAtPosition(editor, this.getPosition(editor), insertText);
        const newPosition = this.getPosition(editor);
        // Set the selection to cover exactly the text part
        this.setSelection(editor, {
            startLineNumber: newPosition.lineNumber,
            endLineNumber: newPosition.lineNumber,
            startColumn: newPosition.column - text.length,
            endColumn: newPosition.column,
        });
        editor.focus();
    }
}
