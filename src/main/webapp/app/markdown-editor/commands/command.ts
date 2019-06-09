import { AceEditorComponent } from 'ng2-ace-editor';

/**
 * abstract class for all commands - default and domain commands of ArTEMiS
 * default commands: markdown commands e.g. bold, italic
 * domain commands: ArTEMiS customized commands
 */
export abstract class Command {
    buttonIcon: string;
    buttonTranslationString: string;
    protected aceEditorContainer: AceEditorComponent;

    public setEditor(aceEditorContainer: AceEditorComponent): void {
        this.aceEditorContainer = aceEditorContainer;
    }

    protected getSelectedText(): string {
        return this.aceEditorContainer.getEditor().getSelectedText();
    }

    protected insertText(text: string) {
        this.aceEditorContainer.getEditor().insert(text);
    }

    protected focus() {
        this.aceEditorContainer.getEditor().focus();
    }

    protected getRange(): Range {
        return this.aceEditorContainer.getEditor().selection.getRange();
    }

    protected replace(range: Range, text: string) {
        this.aceEditorContainer.getEditor().session.replace(range, text);
    }

    protected clearSelection() {
        this.aceEditorContainer.getEditor().clearSelection();
    }

    protected moveCursorTo(row: number, column: number) {
        this.aceEditorContainer.getEditor().moveCursorTo(row, column);
    }

    protected getCursorPosition() {
        return this.aceEditorContainer.getEditor().getCursorPosition();
    }

    protected getLine(row: number) {
        return this.aceEditorContainer
            .getEditor()
            .getSession()
            .getLine(row);
    }

    protected getCurrentLine() {
        const cursor = this.getCursorPosition();
        return this.getLine(cursor.row);
    }

    protected moveCursorToEndOfRow() {
        const cursor = this.getCursorPosition();
        const currentLine = this.aceEditorContainer
            .getEditor()
            .getSession()
            .getLine(cursor.row);
        this.clearSelection();
        this.moveCursorTo(cursor.row, currentLine.length);
    }

    protected addToCompleters(completer: any) {
        this.aceEditorContainer.getEditor().completers = [...(this.aceEditorContainer.getEditor().completers || []), completer];
    }

    abstract execute(input?: string): void;
}
