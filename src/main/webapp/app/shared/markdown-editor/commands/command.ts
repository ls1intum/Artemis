import { ElementRef } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import ace from 'brace';

// Work around to update the range
const Range = ace.acequire('ace/range').Range;

// Required to access end and start
class RangeContainer {
    end: Position;
    start: Position;
}

class Position {
    row: number;
    column: number;
}

/**
 * abstract class for all commands - default and domain commands of Artemis
 * default commands: markdown commands e.g. bold, italic
 * domain commands: Artemis customized commands
 */
export abstract class Command {
    buttonIcon: IconProp;
    buttonTranslationString: string;
    protected aceEditor: any;
    protected markdownWrapper: ElementRef;

    public setEditor(aceEditor: any): void {
        this.aceEditor = aceEditor;
    }

    public setMarkdownWrapper(ref: ElementRef) {
        this.markdownWrapper = ref;
    }

    protected getSelectedText(): string {
        return this.aceEditor.getSelectedText();
    }

    protected getExtendedSelectedText(): string[] {
        const text = this.getText();

        // Split text by line breaks
        const lines = text.split('\n');

        const range = this.getRange() as unknown as RangeContainer;

        // Update the range
        this.aceEditor.selection.setRange(new Range(range.start.row, 0, range.end.row, lines[range.end.row].length));

        // Return extended selection as array
        return lines.slice(range.start.row, range.end.row + 1);
    }

    protected getText(): string {
        return this.aceEditor.getValue();
    }

    protected insertText(text: string) {
        this.aceEditor.insert(text);
    }

    protected focus() {
        this.aceEditor.focus();
    }

    protected getRange(): Range {
        return this.aceEditor.selection.getRange();
    }

    protected replace(range: Range, text: string) {
        this.aceEditor.session.replace(range, text);
    }

    protected clearSelection() {
        this.aceEditor.clearSelection();
    }

    protected moveCursorTo(row: number, column: number) {
        this.aceEditor.moveCursorTo(row, column);
    }

    protected getCursorPosition() {
        return this.aceEditor.getCursorPosition();
    }

    protected getLine(row: number) {
        return this.aceEditor.getSession().getLine(row);
    }

    protected getCurrentLine() {
        const cursor = this.getCursorPosition();
        return this.getLine(cursor.row);
    }

    protected moveCursorToEndOfRow() {
        const cursor = this.getCursorPosition();
        const currentLine = this.aceEditor.getSession().getLine(cursor.row);
        this.clearSelection();
        this.moveCursorTo(cursor.row, currentLine.length);
    }

    protected addCompleter(completer: any) {
        this.aceEditor.completers = [...(this.aceEditor.completers || []), completer];
    }

    abstract execute(input?: string): void;

    protected deleteWhiteSpace(text: string) {
        return text.trim();
    }

    protected addRefinedText(selectedText: string, textToAdd: string) {
        if (selectedText.charAt(0) === ' ' && selectedText.charAt(selectedText.length - 1) === ' ') {
            return this.insertText(' ' + textToAdd + ' ');
        } else if (selectedText.charAt(0) === ' ') {
            return this.insertText(' ' + textToAdd);
        } else if (selectedText.charAt(selectedText.length - 1) === ' ') {
            return this.insertText(textToAdd + ' ');
        } else {
            return this.insertText(textToAdd);
        }
    }
}
