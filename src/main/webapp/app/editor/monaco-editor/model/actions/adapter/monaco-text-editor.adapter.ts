import { TextEditor } from 'app/editor/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorAction } from 'app/editor/monaco-editor/model/actions/text-editor-action.model';
import { Disposable, EditorPosition, MonacoEditorTextModel } from 'app/editor/monaco-editor/model/actions/monaco-editor.util';
import * as monaco from 'monaco-editor';
import { TextEditorCompleter } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-completer.model';
import { TextEditorRange, makeTextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';
import { TextEditorPosition } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-position.model';
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
        const languageId = model.getLanguageId();
        const modelId = model.id;

        return monaco.languages.registerCompletionItemProvider(languageId, {
            provideCompletionItems: async (model: MonacoEditorTextModel, position: EditorPosition): Promise<monaco.languages.CompletionList | undefined> => {
                if (model.id !== modelId) {
                    return undefined;
                }
                const wordUntilPosition = model.getWordUntilPosition(position);
                const range = makeTextEditorRange(position.lineNumber, wordUntilPosition.startColumn, position.lineNumber, wordUntilPosition.endColumn);
                const items = (await completer.searchItems(wordUntilPosition.word)).map((item) => completer.mapCompletionItem(item, range));

                return {
                    suggestions: items.map((item) => {
                        return {
                            label: item.getLabel(),
                            kind: monaco.languages.CompletionItemKind.Constant,
                            insertText: item.getInsertText(),
                            range: this.toMonacoRange(item.getRange()),
                            detail: item.getDetailText(),
                        };
                    }),
                    incomplete: false,
                };
            },
        });
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

    triggerCompletion(): void {
        this.editor.trigger('MonacoTextEditorAdapter::triggerCompletion', 'editor.action.triggerSuggest', {});
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

    addPasteListener(callback: (insertedText: string) => void | Promise<void>): Disposable {
        return this.editor.onDidPaste((pasteEvent) => {
            void callback(this.getTextAtRange(this.fromMonacoRange(pasteEvent.range)));
        });
    }

    /**
     * Adds a capture-phase paste listener to the editor's DOM node that calls the handler with the pasted text before Monaco inserts it.
     * Only pastes into the editor text are intercepted, not pastes into widgets such as the find input.
     * The handler's edit is kept as its own undo step. If the handler returns true, the paste event is cancelled.
     * @param handler The handler to call with the pasted text. Returns true if it handled the paste.
     * @return A disposable that removes the listener.
     */
    addPasteInterceptor(handler: (pastedText: string) => boolean): Disposable {
        const domNode = this.editor.getDomNode();
        if (!domNode) {
            return { dispose: () => {} };
        }
        // Monaco handles the paste event on an element inside its DOM node, so a capture listener on the DOM node runs before it.
        const listener = (event: ClipboardEvent) => {
            const pastedText = event.clipboardData?.getData('text/plain');
            const selectionCount = this.editor.getSelections()?.length ?? 0;
            // Widgets such as the find and replace inputs sit inside the DOM node too; a paste there leaves the text without focus.
            if (!pastedText || !this.editor.hasTextFocus() || this.editor.getOption(monaco.editor.EditorOption.readOnly) || selectionCount > 1) {
                return;
            }
            // Undo stops on both sides keep the handler's edit a separate undo step.
            this.editor.pushUndoStop();
            const handled = handler(pastedText);
            this.editor.pushUndoStop();
            if (handled) {
                event.preventDefault();
                event.stopPropagation();
            }
        };
        domNode.addEventListener('paste', listener, { capture: true });
        return { dispose: () => domNode.removeEventListener('paste', listener, { capture: true }) };
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

    private toMonacoKeybinding(keybinding: TextEditorKeybinding): number {
        const keyCode = MonacoTextEditorAdapter.KEY_CODE_MAP.get(keybinding.getKey()) ?? monaco.KeyCode.Unknown;
        const modifier = MonacoTextEditorAdapter.MODIFIER_MAP.get(keybinding.getModifier()) ?? 0;
        return keyCode | modifier;
    }

    getFullText(): string {
        const firstPosition = new TextEditorPosition(1, 1);
        const lastPosition = this.getEndPosition();
        const range = new TextEditorRange(firstPosition, lastPosition);
        return this.getTextAtRange(range);
    }
}
