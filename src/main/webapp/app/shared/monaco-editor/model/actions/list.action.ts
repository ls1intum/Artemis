import { TextStyleTextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';
import { TextEditorPosition } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-position.model';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { TextEditorKeybinding } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-keybinding.model';

/**
 * Abstract class representing a list action in a text editor.
 * This class handles adding and removing list prefixes and supports event listeners
 * for features like continuing lists with Shift/Cmd+Enter.
 *
 * @abstract
 * @extends TextStyleTextEditorAction
 */
export abstract class ListAction extends TextStyleTextEditorAction {
    protected static editorsWithListener = new WeakMap<TextEditor, boolean>();

    protected abstract readonly PREFIX: string;
    protected abstract getPrefix(lineNumber: number): string;

    /**
     * Constructor for the ListAction class.
     * @param {string} id - The unique ID of the action.
     * @param {string} label - The label of the action.
     * @param {IconDefinition} icon - The icon to display for the action.
     * @param {TextEditorKeybinding[]} shortcut - The keyboard shortcut for the action.
     */
    protected constructor(id: string, label: string, icon: IconDefinition | undefined, shortcut: TextEditorKeybinding[] | undefined) {
        super(id, label, icon, shortcut);
    }

    /**
     * Removes any list prefix (either bullet or numbered) from the given line.
     * @param {string} line - The line to process.
     * @returns {string} - The line without any list prefix.
     */
    protected stripAnyListPrefix(line: string): string {
        const numberedListRegex = /^\s*\d+\.\s+/;
        const bulletListRegex = /^\s*[-*+]\s+/;

        if (numberedListRegex.test(line)) {
            return line.replace(numberedListRegex, '');
        } else if (bulletListRegex.test(line)) {
            return line.replace(bulletListRegex, '');
        }
        return line;
    }

    /**
     * Adds or removes a list prefix from the selected text.
     * Also handles the Shift/Cmd+Enter key combination to continue the list.
     * @param {TextEditor} editor - The editor instance where the action is applied.
     */
    run(editor: TextEditor) {
        if (!ListAction.editorsWithListener.has(editor)) {
            ListAction.editorsWithListener.set(editor, true);

            editor.getDomNode()?.addEventListener('keydown', (event: KeyboardEvent) => {
                if (event.key === 'Enter' && event.shiftKey) {
                    event.preventDefault();
                    this.handleShiftEnter(editor);
                } else if (event.key === 'Enter' && event.metaKey) {
                    event.preventDefault();
                    event.stopPropagation();
                    this.handleShiftEnter(editor);
                } else if (event.key === 'Backspace') {
                    this.handleBackspace(editor, event);
                }
            });
        }

        const selection = editor.getSelection();
        if (!selection) {
            return;
        }

        const selectedText = editor.getTextAtRange(selection);
        const lines = selectedText.split('\n');

        // Check if the cursor is at the end of the line to add or remove the prefix
        const position = editor.getPosition();
        if (position) {
            const currentLineText = editor.getLineText(position.getLineNumber());

            if (!selectedText && position.getColumn() <= currentLineText.length) {
                return;
            }

            if (position.getColumn() === currentLineText.length + 1) {
                const newPrefix = this.getPrefix(1);

                if (currentLineText.startsWith(newPrefix)) {
                    return;
                }

                const updatedLine = newPrefix + currentLineText;

                editor.replaceTextAtRange(
                    new TextEditorRange(new TextEditorPosition(position.getLineNumber(), 1), new TextEditorPosition(position.getLineNumber(), currentLineText.length + 1)),
                    updatedLine,
                );
                editor.focus();
                return;
            }
        }

        const startLineNumber = selection.getStartPosition().getLineNumber();
        const currentPrefix = this.getPrefix(startLineNumber);

        // Determine if all lines have the current prefix
        let allLinesHaveCurrentPrefix;
        if (this.getPrefix(1) != '- ') {
            const numberedListRegex = /^\s*\d+\.\s+/;
            allLinesHaveCurrentPrefix = lines.every((line) => numberedListRegex.test(line));
        } else {
            allLinesHaveCurrentPrefix = lines.every((line) => line.startsWith(currentPrefix));
        }

        let updatedLines: string[];
        if (allLinesHaveCurrentPrefix) {
            updatedLines = lines.map((line) => this.stripAnyListPrefix(line));
        } else {
            const linesWithoutPrefix = lines.map((line) => this.stripAnyListPrefix(line));

            updatedLines = linesWithoutPrefix.map((line, index) => {
                const prefix = this.getPrefix(index) != '- ' ? this.getPrefix(index + 1) : this.getPrefix(startLineNumber + index);
                return prefix + line;
            });
        }

        const updatedText = updatedLines.join('\n');
        editor.replaceTextAtRange(selection, updatedText);
        editor.focus();
    }

    /**
     * Checks if a line has any list prefix (either bullet or numbered).
     * @param {string} line - The line to check.
     * @returns {boolean} - True if the line has a prefix, false otherwise.
     */
    protected hasPrefix(line: string): boolean {
        const numberedListRegex = /^\s*\d+\.\s+/;
        const bulletListRegex = /^\s*[-\-*+]\s+/;
        return numberedListRegex.test(line) || bulletListRegex.test(line);
    }

    /**
     * Handles the Shift+Enter key combination to continue the list.
     * @param {TextEditor} editor - The editor instance.
     */
    protected handleShiftEnter(editor: TextEditor) {
        const position = editor.getPosition();
        if (position) {
            const currentLineText = editor.getLineText(position.getLineNumber());
            let nextLinePrefix = '';

            // Check if the current line starts with a prefix and continue the list
            if (this.hasPrefix(currentLineText)) {
                const isNumbered = /^\s*\d+\.\s+/.test(currentLineText);

                if (isNumbered) {
                    const match = currentLineText.match(/^\s*(\d+)\.\s+/);
                    const currentNumber = match ? parseInt(match[1], 10) : 0;
                    nextLinePrefix = `${currentNumber + 1}. `;
                } else {
                    nextLinePrefix = '- ';
                }
            }

            editor.replaceTextAtRange(new TextEditorRange(position, position), '\n' + nextLinePrefix);

            const newLineNumber = position.getLineNumber() + 1;
            const newColumnPosition = nextLinePrefix ? nextLinePrefix.length + 1 : 1;
            editor.setPosition(new TextEditorPosition(newLineNumber, newColumnPosition));
            editor.focus();
        }
    }

    /**
     * Handles the Backspace key press to remove a prefix if the cursor is just after it.
     * @param {TextEditor} editor - The editor instance.
     * @param {KeyboardEvent} event - The keyboard event.
     */
    protected handleBackspace(editor: TextEditor, event: KeyboardEvent) {
        const position = editor.getPosition();
        if (position) {
            const lineNumber = position.getLineNumber();
            const lineContent = editor.getLineText(lineNumber);
            const linePrefixMatch = lineContent.match(/^\s*(\d+\.\s+|[-*+]\s+)/);

            if (linePrefixMatch) {
                const prefixLength = linePrefixMatch[0].length;
                // Check if the cursor is just after the prefix
                if (position.getColumn() === prefixLength + 1) {
                    event.preventDefault();
                    editor.replaceTextAtRange(new TextEditorRange(new TextEditorPosition(lineNumber, 1), new TextEditorPosition(lineNumber, prefixLength + 1)), ' ');
                    editor.focus();
                }
            }
        }
    }
}
