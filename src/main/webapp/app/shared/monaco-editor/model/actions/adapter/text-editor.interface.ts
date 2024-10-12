import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { Disposable } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { TextEditorPosition } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-position.model';
import { TextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';
import { TextEditorCompleter } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-completer.model';

export interface TextEditor {
    /**
     * Adds an action to the editor. An action should only be registered in one editor at a time.
     * @param action The action to add to the editor.
     * @return A disposable that can be used to remove the action from the editor.
     */
    addAction(action: TextEditorAction): Disposable;

    /**
     * Executes the given action in the editor. It must be an action that has been added to the editor.
     * @param action The action to execute.
     * @param args Optional arguments to pass to the action.
     */
    executeAction(action: TextEditorAction, args?: object): void;

    /**
     * Layouts the editor's content and dimensions, e.g. after a resize.
     */
    layout(): void;

    /**
     * Focuses the input area of the editor.
     */
    focus(): void;

    /**
     * Replaces the text in the editor at the given range with the given text.
     * @param range The range to replace the text at.
     * @param text The text to replace the range with.
     */
    replaceTextAtRange(range: TextEditorRange, text: string): void;

    /**
     * Gets the DOM node associated with the editor, or undefined if no such node exists.
     */
    getDomNode(): HTMLElement | undefined;

    /**
     * Triggers the completion in the editor, e.g. by showing a widget.
     */
    triggerCompletion(): void;

    /**
     * Retrieves the text at the given range in the editor.
     * Line endings are normalized to '\n'. If no suitable text exists, an empty string is returned.
     * @param range The range to get the text from.
     */
    getTextAtRange(range: TextEditorRange): string;

    /**
     * Retrieves the text of the line at the given line number in the editor.
     * @param lineNumber The line number to get the text from. Line numbers start at 1.
     */
    getLineText(lineNumber: number): string;

    /**
     * Retrieves the number of lines in the editor.
     */
    getNumberOfLines(): number;

    /**
     * Gets the position after the last character in the editor.
     */
    getEndPosition(): TextEditorPosition;

    /**
     * Gets the position of the cursor in the editor.
     * If no suitable position exists, the default position is returned (1, 1).
     */
    getPosition(): TextEditorPosition;

    /**
     * Sets the position of the cursor in the editor.
     * @param position The position to set the cursor to. Line numbers and columns start at 1.
     */
    setPosition(position: TextEditorPosition): void;

    /**
     * Gets the range representing the current selection in the editor.
     * The range may be empty, in which case it represents the position of the cursor (in this case, the start and end positions are the same).
     * If no suitable range exists, the default range is returned (1, 1) to (1, 1).
     */
    getSelection(): TextEditorRange;

    /**
     * Sets the selection in the editor.
     * @param selection The range to set the selection to. Line numbers and columns start at 1.
     */
    setSelection(selection: TextEditorRange): void;

    /**
     * Reveals a range of text in the center of the editor by instantly scrolling to it.
     * @param range The range to reveal.
     */
    revealRange(range: TextEditorRange): void;

    /**
     * Adds a completer to the editor.
     * @param completer The completer to add to the editor.
     * @return A disposable that can be used to remove the completer from the editor.
     */
    addCompleter<ItemType>(completer: TextEditorCompleter<ItemType>): Disposable;
}
