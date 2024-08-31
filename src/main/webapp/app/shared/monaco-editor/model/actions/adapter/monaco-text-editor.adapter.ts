import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { Disposable } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import * as monaco from 'monaco-editor';
import { TextEditorCompleter } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-completer.model';
import { TextEditorRange, makeTextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';
import { TextEditorPosition } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-position.model';

export class MonacoTextEditorAdapter implements TextEditor {
    constructor(private editor: monaco.editor.IStandaloneCodeEditor) {}

    addAction(action: MonacoEditorAction): Disposable {
        const actionDescriptor = {
            id: action.id,
            label: action.label,
            keybindings: action.keybindings,
            run: () => {
                action.run(this as any); // TODO
            },
        };
        return this.editor.addAction(actionDescriptor);
    }

    executeAction(action: MonacoEditorAction, args?: object): void {
        this.editor.trigger('MonacoTextEditorAdapter::executeAction', action.id, args);
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    addCompleter<ItemType>(completer: TextEditorCompleter<ItemType>): Disposable {
        // TODO
        return { dispose: () => {} };
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
}
