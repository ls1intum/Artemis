import { ElementRef } from '@angular/core';
import { AceEditorComponent } from 'ng2-ace-editor';

/**
 * abstract class for all commands - default and domain commands of Artemis
 * default commands: markdown commands e.g. bold, italic
 * domain commands: Artemis customized commands
 */
export abstract class Command {
    buttonIcon: string;
    buttonTranslationString: string;
    startSpace: string;
    endSpace: string;
    protected aceEditorContainer: AceEditorComponent;
    protected markdownWrapper: ElementRef;

    public setEditor(aceEditorContainer: AceEditorComponent): void {
        this.aceEditorContainer = aceEditorContainer;
    }

    public setMarkdownWrapper(ref: ElementRef) {
        this.markdownWrapper = ref;
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

    protected addCompleter(completer: any) {
        this.aceEditorContainer.getEditor().completers = [...(this.aceEditorContainer.getEditor().completers || []), completer];
    }

    abstract execute(input?: string): void;

    protected ignoreWhiteSpace(text: string) {
        const textLength = text.length;
        let startIndex = 0;
        let endIndex = 0;
        this.startSpace = '';
        this.endSpace = '';

        for (let i = 0; i < textLength; i++) {
            if (text.charAt(i) === ' ') {
                this.startSpace = this.startSpace + ' ';
            } else {
                startIndex = i;
                break;
            }
        }

        for (let j = textLength; j >= 0; j--) {
            if (text.charAt(j - 1) === ' ') {
                this.endSpace = this.endSpace + ' ';
            } else {
                endIndex = j;
                break;
            }
        }
        return text.slice(startIndex, endIndex);
    }

    protected addRefinedTxt(text: string) {
        return this.insertText(this.startSpace + text + this.endSpace);
    }
}
