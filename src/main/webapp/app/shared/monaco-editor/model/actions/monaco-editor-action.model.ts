import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { TranslateService } from '@ngx-translate/core';
import * as monaco from 'monaco-editor';
import { enterFullscreen, exitFullscreen, isFullScreen } from 'app/shared/util/fullscreen.util';

export abstract class MonacoEditorAction implements monaco.editor.IActionDescriptor, monaco.IDisposable {
    // IActionDescriptor
    id: string;
    label: string;
    translationKey: string;
    keybindings?: number[];

    icon?: IconDefinition;

    /**
     * The disposable action that is returned by `editor.addAction`. This is required to unregister the action from the editor.
     */
    disposableAction?: monaco.IDisposable;
    /**
     * The editor (if any) this action is registered in.
     * @private
     */
    private _editor?: monaco.editor.IStandaloneCodeEditor;

    /**
     * Create a new action with the given id, translation key, icon, and keybindings.
     * @param id The unique identifier of the action.
     * @param translationKey The translation key of the action label.
     * @param icon The icon to display in the editor toolbar, if any.
     * @param keybindings The keybindings to trigger the action, if any.
     */
    constructor(id: string, translationKey: string, icon?: IconDefinition, keybindings?: number[]) {
        this.id = id;
        this.translationKey = translationKey;
        this.icon = icon;
        this.keybindings = keybindings;
    }

    /**
     * The actual implementation of the action. This method is called by Monaco when the action is triggered in the editor.
     * @param editor The editor in which the action was triggered.
     * @param args Optional arguments that can be passed to the action. To ensure this is an object, you can use {@link executeInCurrentEditor}.
     */
    abstract run(editor: monaco.editor.ICodeEditor, args?: unknown): void;

    /**
     * Execute the action in the current editor. This is a convenience method to trigger the action in the editor without having to pass the editor instance.
     * Furthermore, it keeps the argument handling consistent between the real editor (which passes undefined for no arguments) and the test environment (which passes {} for no arguments).
     * Override this method to define type-safe arguments.
     * @param args Optional arguments that can be passed to the action.
     */
    executeInCurrentEditor(args: object = {}): void {
        if (!this._editor) {
            throw new Error('Action is not registered in an editor.');
        }
        this._editor.trigger(`${this.id}::executeInCurrentEditor`, this.id, args);
    }

    /**
     * Dispose the action if it has been registered in an editor.
     */
    dispose(): void {
        this.disposableAction?.dispose();
        this._editor = undefined;
    }

    /**
     * Register this action in the given editor. This is required to make the action available in the editor. Note that the action can only be registered in one editor at a time.
     * @param editor The editor to register this action in. Note that its type has to be `monaco.editor.IStandaloneCodeEditor` to ensure it supports the `addAction` method.
     * @param translateService The translation service to use for translating the action label.
     * @throws error if the action has already been registered in an editor.
     */
    register(editor: monaco.editor.IStandaloneCodeEditor, translateService: TranslateService): void {
        if (this.disposableAction) {
            throw new Error(`Action (id ${this.id}) already belongs to an editor. Dispose it first before registering it in another editor.`);
        }
        this.label = translateService.instant(this.translationKey);
        this.disposableAction = editor.addAction(this);
        this._editor = editor;
    }

    /**
     * Toggles the given delimiter around the current selection or inserts it at the current cursor position if there is no selection.
     * In the latter case, textToInsert is inserted between the delimiters.
     * @param editor The editor to toggle the delimiter in.
     * @param openDelimiter The opening delimiter, e.g. <ins>.
     * @param closeDelimiter The closing delimiter, e.g. </ins>.
     * @param textToInsert The text to insert between the delimiters if there is no selection. Defaults to an empty string.
     */
    toggleDelimiterAroundSelection(editor: monaco.editor.ICodeEditor, openDelimiter: string, closeDelimiter: string, textToInsert: string = ''): void {
        const selection = editor.getSelection();
        const selectedText = selection ? this.getTextAtRange(editor, selection)?.trim() : undefined;
        const position = editor.getPosition();
        if (selection && selectedText) {
            // If the selected text is already surrounded by the delimiters, remove them. Note that the closeDelimiter may be empty, in which case we pass undefined to preserve the text.
            const textToInsert = this.isTextSurroundedByDelimiters(selectedText, openDelimiter, closeDelimiter)
                ? selectedText.slice(openDelimiter.length, -closeDelimiter.length || undefined)
                : `${openDelimiter}${selectedText}${closeDelimiter}`;
            this.replaceTextAtRange(editor, selection, textToInsert);
        } else if (position) {
            this.insertTextAtPosition(editor, position, `${openDelimiter}${textToInsert}${closeDelimiter}`);
            // Move the cursor to the end of the inserted text. Note that the delimiters may have newlines.
            const textBeforeCursor = `${openDelimiter}${textToInsert}`;
            const lines = textBeforeCursor.split('\n');
            const newLineNumber = position.lineNumber + lines.length - 1;
            const newColumn = lines.length === 1 ? position.column + lines[0].length : lines[lines.length - 1].length + 1;
            editor.setPosition({ lineNumber: newLineNumber, column: newColumn });
        }
    }

    /**
     * Checks if the given text is surrounded by the given delimiters. This requires that the text is long enough to contain both delimiters.
     * @param text The text to check.
     * @param openDelimiter The opening delimiter.
     * @param closeDelimiter The closing delimiter.
     */
    isTextSurroundedByDelimiters(text: string, openDelimiter: string, closeDelimiter: string): boolean {
        return text.startsWith(openDelimiter) && text.endsWith(closeDelimiter) && text.length >= openDelimiter.length + closeDelimiter.length;
    }

    /**
     * Replaces the text at the current selection with the given text. If there is no selection, the text is inserted at the current cursor position.
     * @param editor The editor to replace the text in.
     * @param text The text to replace the current selection with.
     */
    replaceTextAtCurrentSelection(editor: monaco.editor.ICodeEditor, text: string): void {
        const selection = editor.getSelection();
        const selectedText = selection ? this.getTextAtRange(editor, selection)?.trim() : undefined;
        if (selection && selectedText !== undefined) {
            this.replaceTextAtRange(editor, selection, text);
        }
    }

    /**
     * Inserts the given text at the current cursor position.
     * @param editor The editor to insert the text in.
     * @param position The position to insert the text at.
     * @param text The text to insert.
     */
    insertTextAtPosition(editor: monaco.editor.ICodeEditor, position: monaco.IPosition, text: string): void {
        this.replaceTextAtRange(editor, new monaco.Range(position.lineNumber, position.column, position.lineNumber, position.column), text);
    }

    /**
     * Replaces the text at the given range with the given text.
     * @param editor The editor to replace the text in.
     * @param range The range to replace the text at.
     * @param text The text to replace the range with.
     */
    replaceTextAtRange(editor: monaco.editor.ICodeEditor, range: monaco.IRange, text: string): void {
        editor.executeEdits(this.id, [{ range, text }]);
    }

    /**
     * Deletes the text at the given range.
     * @param editor The editor to delete the text in.
     * @param range The range to delete the text at.
     */
    deleteTextAtRange(editor: monaco.editor.ICodeEditor, range: monaco.IRange): void {
        this.replaceTextAtRange(editor, range, '');
    }

    /**
     * Gets the text at the given range. If the range is empty, undefined is returned.
     * @param editor The editor to get the text from.
     * @param range The range to get the text from.
     */
    getTextAtRange(editor: monaco.editor.ICodeEditor, range: monaco.IRange): string | undefined {
        // End of line preference is important here. Otherwise, Windows may use CRLF line endings.
        return editor.getModel()?.getValueInRange(range, monaco.editor.EndOfLinePreference.LF);
    }

    /**
     * Gets the text of the line at the given line number.
     * @param editor The editor to get the text from.
     * @param lineNumber The line number to get the text from. Line numbers start at 1.
     */
    getLineText(editor: monaco.editor.ICodeEditor, lineNumber: number): string | undefined {
        return editor.getModel()?.getLineContent(lineNumber);
    }

    /**
     * Toggles the fullscreen mode of the given element. If no element is provided, the editor's DOM node is used.
     * @param editor The editor to toggle the fullscreen mode for.
     * @param element The element to toggle the fullscreen mode for.
     */
    toggleFullscreen(editor: monaco.editor.ICodeEditor, element?: HTMLElement): void {
        const fullscreenElement = element ?? editor.getDomNode();
        if (isFullScreen()) {
            exitFullscreen();
        } else if (fullscreenElement) {
            enterFullscreen(fullscreenElement);
        }
        editor.layout();
    }
}
