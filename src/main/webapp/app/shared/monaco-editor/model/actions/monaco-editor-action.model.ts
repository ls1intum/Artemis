import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { TranslateService } from '@ngx-translate/core';
import * as monaco from 'monaco-editor';
import { enterFullscreen, exitFullscreen, isFullScreen } from 'app/shared/util/fullscreen.util';
import { Disposable, EditorPosition, EditorRange, MonacoEditorTextModel, makeEditorRange } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-adapter.model';

export abstract class MonacoEditorAction implements monaco.editor.IActionDescriptor, Disposable {
    // IActionDescriptor
    id: string;
    label: string;
    translationKey: string;
    keybindings?: number[];

    icon?: IconDefinition;
    readonly hideInEditor: boolean;

    /**
     * The disposable action that is returned by `editor.addAction`. This is required to unregister the action from the editor.
     */
    disposableAction?: Disposable;
    /**
     * The editor (if any) this action is registered in.
     * @private
     */
    private _editor?: TextEditor;

    /**
     * Create a new action with the given id, translation key, icon, and keybindings.
     * @param id The unique identifier of the action.
     * @param translationKey The translation key of the action label.
     * @param icon The icon to display in the editor toolbar, if any.
     * @param keybindings The keybindings to trigger the action, if any.
     * @param hideInEditor Whether to hide the action in the editor toolbar. Defaults to false.
     */
    constructor(id: string, translationKey: string, icon?: IconDefinition, keybindings?: number[], hideInEditor?: boolean) {
        this.id = id;
        this.translationKey = translationKey;
        this.icon = icon;
        this.keybindings = keybindings;
        this.hideInEditor = hideInEditor ?? false;
    }

    /**
     * The actual implementation of the action. This method is called by Monaco when the action is triggered in the editor.
     * @param editor The editor in which the action was triggered.
     * @param args Optional arguments that can be passed to the action. To ensure this is an object, you can use {@link executeInCurrentEditor}.
     */
    abstract run(editor: TextEditor, args?: unknown): void;

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
        this.disposableAction = undefined;
        this._editor = undefined;
    }

    /**
     * Register this action in the given editor. This is required to make the action available in the editor. Note that the action can only be registered in one editor at a time.
     * @param editor The editor to register this action in.
     * @param translateService The translation service to use for translating the action label.
     * @throws error if the action has already been registered in an editor.
     */
    register(editor: TextEditor, translateService: TranslateService): void {
        if (this.disposableAction) {
            throw new Error(`Action (id ${this.id}) already belongs to an editor. Dispose it first before registering it in another editor.`);
        }
        this.label = translateService.instant(this.translationKey);
        this.disposableAction = editor.addAction(this);
        this._editor = editor;
    }

    /**
     * Registers a completion provider for the current model of the given editor. This is useful to provide completion items for a specific editor, which is not supported by the monaco API.
     * @param editor The editor whose model to register the completion provider for.
     * @param searchFn Function that returns all relevant items for the current search term. Note that Monaco also filters the items based on the user input.
     * @param mapToSuggestionFn Function that maps an item to a Monaco completion suggestion.
     * @param triggerCharacter The character that triggers the completion provider.
     * @param listIncomplete Whether the list of suggestions is incomplete. If true, Monaco will keep searching for more suggestions.
     */
    registerCompletionProviderForCurrentModel<ItemType>(
        editor: TextEditor,
        searchFn: (searchTerm?: string) => Promise<ItemType[]>,
        mapToSuggestionFn: (item: ItemType, range: EditorRange) => monaco.languages.CompletionItem,
        triggerCharacter?: string,
        listIncomplete?: boolean,
    ): monaco.IDisposable {
        const model = editor.getModel();
        if (!model) {
            throw new Error(`A model must be attached to the editor to register a completion provider.`);
        }
        if (triggerCharacter !== undefined && triggerCharacter.length !== 1) {
            throw new Error(`The trigger character must be a single character.`);
        }
        const languageId = model.getLanguageId();
        const modelId = model.id;
        // We have to subtract an offset of 1 from the start column to include the trigger character in the range that will be replaced.
        const triggerCharacterOffset = triggerCharacter ? 1 : 0;
        return monaco.languages.registerCompletionItemProvider(languageId, {
            // We only want to trigger the completion provider if the trigger character is typed. However, we also allow numbers to trigger the completion, as they would not normally trigger it.
            triggerCharacters: triggerCharacter ? [triggerCharacter, ...'0123456789'] : undefined,
            provideCompletionItems: async (model: MonacoEditorTextModel, position: EditorPosition): Promise<monaco.languages.CompletionList | undefined> => {
                if (model.id !== modelId) {
                    return undefined;
                }
                const sequenceUntilPosition = this.findTypedSequenceUntilPosition(model, position, triggerCharacter);
                if (!sequenceUntilPosition) {
                    return undefined;
                }
                const range = {
                    startLineNumber: position.lineNumber,
                    startColumn: sequenceUntilPosition.startColumn - triggerCharacterOffset,
                    endLineNumber: position.lineNumber,
                    endColumn: sequenceUntilPosition.endColumn,
                };
                const beforeWord = model.getValueInRange({
                    startLineNumber: position.lineNumber,
                    startColumn: sequenceUntilPosition.startColumn - triggerCharacterOffset,
                    endLineNumber: position.lineNumber,
                    endColumn: sequenceUntilPosition.startColumn,
                });

                // We only want suggestions if the trigger character is at the beginning of the word.
                if (triggerCharacter && sequenceUntilPosition.word !== triggerCharacter && beforeWord !== triggerCharacter) {
                    return undefined;
                }
                const items = await searchFn(sequenceUntilPosition.word);
                return {
                    suggestions: items.map((item) => mapToSuggestionFn(item, range)),
                    incomplete: listIncomplete,
                };
            },
        });
    }

    /**
     * Finds the sequence of characters that was typed between the trigger character and the current position. If no trigger character is provided, we assume the sequence starts at the beginning of the word (default Monaco behavior).
     * @param model The model to find the typed sequence in.
     * @param position The position until which to find the typed sequence.
     * @param triggerCharacter The character that triggers the sequence. If not provided, the sequence is assumed to start at the beginning of the word.
     * @param lengthLimit The maximum length of the sequence to find. Defaults to 25.
     */
    findTypedSequenceUntilPosition(model: MonacoEditorTextModel, position: EditorPosition, triggerCharacter?: string, lengthLimit = 25): monaco.editor.IWordAtPosition | undefined {
        // Find the sequence of characters that was typed between the trigger character and the current position. If no trigger character is provided, we assume the sequence starts at the beginning of the word.
        if (!triggerCharacter) {
            return model.getWordUntilPosition(position);
        }
        const scanColumn = Math.max(1, position.column - lengthLimit);
        const scanRange = makeEditorRange(position.lineNumber, scanColumn, position.lineNumber, position.column);
        const text = model.getValueInRange(scanRange);
        const triggerIndex = text.lastIndexOf(triggerCharacter);
        if (triggerIndex === -1) {
            return undefined;
        }
        // The word not including the trigger character.
        return {
            word: text.slice(triggerIndex + 1),
            startColumn: scanRange.startColumn + triggerIndex + 1,
            endColumn: position.column,
        };
    }

    /**
     * Toggles the given delimiter around the current selection or inserts it at the current cursor position if there is no selection.
     * In the latter case, textToInsert is inserted between the delimiters.
     * @param editor The editor to toggle the delimiter in.
     * @param openDelimiter The opening delimiter, e.g. <ins>.
     * @param closeDelimiter The closing delimiter, e.g. </ins>.
     * @param textToInsert The text to insert between the delimiters if there is no selection. Defaults to an empty string.
     */
    toggleDelimiterAroundSelection(editor: TextEditor, openDelimiter: string, closeDelimiter: string, textToInsert: string = ''): void {
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
     * Types the given text in the editor at the current cursor position. You can use this e.g. to trigger a suggestion.
     * @param editor The editor to type the text in.
     * @param text The text to type.
     */
    typeText(editor: TextEditor, text: string): void {
        editor.trigger('keyboard', 'type', { text });
    }

    /**
     * Replaces the text at the current selection with the given text. If there is no selection, the text is inserted at the current cursor position.
     * @param editor The editor to replace the text in.
     * @param text The text to replace the current selection with.
     */
    replaceTextAtCurrentSelection(editor: TextEditor, text: string): void {
        const selection = editor.getSelection();
        const selectedText = selection ? this.getTextAtRange(editor, selection)?.trim() : undefined;
        if (selection && selectedText !== undefined) {
            this.replaceTextAtRange(editor, selection, text);
        }
    }

    /**
     * Gets the text of the current selection. If there is no selection, undefined is returned.
     * @param editor The editor to get the selection text from.
     */
    getSelectedText(editor: TextEditor): string | undefined {
        const selection = editor.getSelection();
        return selection ? this.getTextAtRange(editor, selection) : undefined;
    }

    /**
     * Inserts the given text at the current cursor position.
     * @param editor The editor to insert the text in.
     * @param position The position to insert the text at.
     * @param text The text to insert.
     */
    insertTextAtPosition(editor: TextEditor, position: EditorPosition, text: string): void {
        this.replaceTextAtRange(editor, makeEditorRange(position.lineNumber, position.column, position.lineNumber, position.column), text);
    }

    /**
     * Replaces the text at the given range with the given text.
     * @param editor The editor to replace the text in.
     * @param range The range to replace the text at.
     * @param text The text to replace the range with.
     */
    replaceTextAtRange(editor: TextEditor, range: EditorRange, text: string): void {
        editor.executeEdits(this.id, [{ range, text }]);
    }

    /**
     * Deletes the text at the given range.
     * @param editor The editor to delete the text in.
     * @param range The range to delete the text at.
     */
    deleteTextAtRange(editor: TextEditor, range: EditorRange): void {
        this.replaceTextAtRange(editor, range, '');
    }

    /**
     * Gets the text at the given range. If the range is empty, undefined is returned.
     * @param editor The editor to get the text from.
     * @param range The range to get the text from.
     */
    getTextAtRange(editor: TextEditor, range: EditorRange): string | undefined {
        // End of line preference is important here. Otherwise, Windows may use CRLF line endings.
        return editor.getModel()?.getValueInRange(range, monaco.editor.EndOfLinePreference.LF);
    }

    /**
     * Gets the text of the line at the given line number.
     * @param editor The editor to get the text from.
     * @param lineNumber The line number to get the text from. Line numbers start at 1.
     */
    getLineText(editor: TextEditor, lineNumber: number): string | undefined {
        return editor.getModel()?.getLineContent(lineNumber);
    }

    /**
     * Gets the number of lines in the editor.
     * @param editor The editor to get the line count from.
     */
    getLineCount(editor: TextEditor): number {
        return editor.getModel()?.getLineCount() ?? 0;
    }

    /**
     * Gets the position of the last character in the editor.
     * @param editor The editor to get the position from.
     */
    getEndPosition(editor: TextEditor): EditorPosition {
        return editor.getModel()?.getFullModelRange().getEndPosition() ?? { lineNumber: 1, column: 1 };
    }

    /**
     * Sets the position of the cursor in the given editor.
     * @param editor The editor to set the position in.
     * @param position The position to set.
     * @param revealLine Whether to scroll the editor to reveal the line the position is on. Defaults to false.
     */
    setPosition(editor: TextEditor, position: EditorPosition, revealLine = false): void {
        editor.setPosition(position);
        if (revealLine) {
            editor.revealLineInCenter(position.lineNumber);
        }
    }

    getPosition(editor: TextEditor): EditorPosition {
        return editor.getPosition() ?? { lineNumber: 1, column: 1 };
    }

    /**
     * Sets the selection of the given editor to the given range and reveals it in the center of the editor.
     * @param editor The editor to set the selection in.
     * @param selection The selection to set.
     */
    setSelection(editor: TextEditor, selection: EditorRange): void {
        editor.setSelection(selection);
        editor.revealRangeInCenter(selection);
    }

    /**
     * Clears the current selection in the given editor, but preserves the cursor position.
     * @param editor The editor to clear the selection in.
     */
    clearSelection(editor: TextEditor): void {
        const position = this.getPosition(editor);
        this.setSelection(editor, makeEditorRange(position.lineNumber, position.column, position.lineNumber, position.column));
    }

    /**
     * Adjusts the cursor position so it is at the end of the current line.
     * @param editor The editor to adjust the cursor position in.
     */
    moveCursorToEndOfLine(editor: TextEditor): void {
        const position: EditorPosition = { ...this.getPosition(editor), column: Number.POSITIVE_INFINITY };
        this.setPosition(editor, position);
    }

    /**
     * Toggles the fullscreen mode of the given element. If no element is provided, the editor's DOM node is used.
     * @param editor The editor to toggle the fullscreen mode for.
     * @param element The element to toggle the fullscreen mode for.
     */
    toggleFullscreen(editor: TextEditor, element?: HTMLElement): void {
        const fullscreenElement = element ?? editor.getDomNode();
        if (isFullScreen()) {
            exitFullscreen();
        } else if (fullscreenElement) {
            enterFullscreen(fullscreenElement);
        }
        editor.layout();
    }
}
