import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { Disposable, EditorPosition, MonacoEditorTextModel, makeEditorRange } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import * as monaco from 'monaco-editor';
import { TextEditorCompleter } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-completer.model';
import { TextEditorRange, makeTextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';
import { TextEditorPosition } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-position.model';
import { TextEditorCompletionItemKind } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-completion-item.model';
import { TextEditorKeyCode, TextEditorKeyModifier, TextEditorKeybinding } from './text-editor-keybinding.model';

export class MonacoTextEditorAdapter implements TextEditor {
    private static readonly KEY_CODE_MAP = new Map<TextEditorKeyCode, number>([
        [TextEditorKeyCode.KeyB, monaco.KeyCode.KeyB],
        [TextEditorKeyCode.KeyI, monaco.KeyCode.KeyI],
        [TextEditorKeyCode.KeyU, monaco.KeyCode.KeyU],
    ]);

    private static readonly MODIFIER_MAP = new Map<TextEditorKeyModifier, number>([[TextEditorKeyModifier.CtrlCmd, monaco.KeyMod.CtrlCmd]]);

    constructor(private editor: monaco.editor.IStandaloneCodeEditor) {}

    addAction(action: TextEditorAction): Disposable {
        const actionDescriptor: monaco.editor.IActionDescriptor = {
            id: action.id,
            label: action.label,
            keybindings: action.keybindings?.map(this.toMonacoKeybinding),
            run: (_, args) => {
                action.run(this, args);
            },
        };
        return this.editor.addAction(actionDescriptor);
    }

    executeAction(action: TextEditorAction, args?: object): void {
        this.editor.trigger('TextEditorAdapter::executeAction', action.id, args);
    }

    /**
     * Adds a completer to the current model of the editor. This is useful to provide suggestions to the user for a specific editor, which is not supported by the Monaco API.
     * @param completer The completer to add to the editor's current model.
     */
    addCompleter(completer: TextEditorCompleter<unknown>): Disposable {
        const model = this.editor.getModel();
        if (!model) {
            throw new Error(`A model must be attached to the editor to register a completer.`);
        }
        const triggerCharacter = completer.triggerCharacter;
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
                const items = (await completer.searchItems(sequenceUntilPosition.word)).map((item) => completer.mapCompletionItem(item, this.fromMonacoRange(range)));

                return {
                    suggestions: items.map((item) => {
                        return {
                            label: item.getLabel(),
                            kind: this.toMonacoCompletionKind(item.getKind()),
                            insertText: item.getInsertText(),
                            range: this.toMonacoRange(item.getRange()),
                            detail: item.getDetailText(),
                        };
                    }),
                    incomplete: completer.incomplete,
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
    private findTypedSequenceUntilPosition(
        model: monaco.editor.ITextModel,
        position: monaco.IPosition,
        triggerCharacter?: string,
        lengthLimit = 25,
    ): monaco.editor.IWordAtPosition | undefined {
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

    layout(): void {
        this.editor.layout();
    }

    focus(): void {
        this.editor.focus();
    }

    replaceTextAtRange(range: TextEditorRange, text: string): void {
        this.editor.executeEdits('MonacoTextEditorAdapter::replaceTextAtRange', [
            {
                range: this.toMonacoRange(range),
                text,
            },
        ]);
    }

    getDomNode(): HTMLElement | undefined {
        return this.editor.getDomNode() ?? undefined;
    }

    typeText(text: string) {
        this.editor.trigger('MonacoTextEditorAdapter::typeText', 'type', { text });
    }

    getTextAtRange(range: TextEditorRange): string {
        return this.editor.getModel()?.getValueInRange(this.toMonacoRange(range), monaco.editor.EndOfLinePreference.LF) ?? '';
    }

    getLineText(lineNumber: number): string {
        return this.editor.getModel()?.getLineContent(lineNumber) ?? '';
    }

    getNumberOfLines(): number {
        return this.editor.getModel()?.getLineCount() ?? 0;
    }

    getEndPosition(): TextEditorPosition {
        return this.fromMonacoPosition(this.editor.getModel()?.getFullModelRange().getEndPosition() ?? { lineNumber: 1, column: 1 });
    }

    getPosition(): TextEditorPosition {
        const position = this.editor.getPosition() ?? { column: 1, lineNumber: 1 };
        return this.fromMonacoPosition(position);
    }

    setPosition(position: TextEditorPosition): void {
        this.editor.setPosition(this.toMonacoPosition(position));
    }

    getSelection(): TextEditorRange {
        const selection = this.editor.getSelection() ?? { startColumn: 1, startLineNumber: 1, endColumn: 1, endLineNumber: 1 };
        return this.fromMonacoRange(selection);
    }

    setSelection(selection: TextEditorRange): void {
        this.editor.setSelection(this.toMonacoRange(selection));
    }

    revealRange(range: TextEditorRange): void {
        this.editor.revealRangeInCenter(this.toMonacoRange(range));
    }

    private toMonacoPosition(position: TextEditorPosition): monaco.IPosition {
        return new monaco.Position(position.getLineNumber(), position.getColumn());
    }

    private fromMonacoPosition(position: monaco.IPosition): TextEditorPosition {
        return new TextEditorPosition(position.lineNumber, position.column);
    }

    private toMonacoRange(range: TextEditorRange): monaco.IRange {
        const startPosition = range.getStartPosition();
        const endPosition = range.getEndPosition();
        return new monaco.Range(startPosition.getLineNumber(), startPosition.getColumn(), endPosition.getLineNumber(), endPosition.getColumn());
    }

    private fromMonacoRange(range: monaco.IRange): TextEditorRange {
        return makeTextEditorRange(range.startLineNumber, range.startColumn, range.endLineNumber, range.endColumn);
    }

    private toMonacoCompletionKind(kind: TextEditorCompletionItemKind) {
        switch (kind) {
            case TextEditorCompletionItemKind.User:
                return monaco.languages.CompletionItemKind.User;
            default:
                return monaco.languages.CompletionItemKind.Constant;
        }
    }

    private toMonacoKeybinding(keybinding: TextEditorKeybinding): number {
        const keyCode = MonacoTextEditorAdapter.KEY_CODE_MAP.get(keybinding.getKey()) ?? monaco.KeyCode.Unknown;
        const modifier = MonacoTextEditorAdapter.MODIFIER_MAP.get(keybinding.getModifier()) ?? 0;
        return keyCode | modifier;
    }
}
