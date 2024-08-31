import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-adapter.model';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { Disposable } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import * as monaco from 'monaco-editor';
import { TextEditorCompleter } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-completer.model';
import { TextEditorModel } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-model.model';
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

    getModel(): TextEditorModel {
        return new TextEditorModel(); // TODO
    }

    layout(): void {
        this.editor.layout();
    }

    focus(): void {
        this.editor.focus();
    }

    getDomNode(): HTMLElement | undefined {
        return this.editor.getDomNode() ?? undefined;
    }

    getPosition(): TextEditorPosition {
        const position = this.editor.getPosition() ?? { column: 1, lineNumber: 1 };
        return new TextEditorPosition(position.lineNumber, position.column);
    }

    setPosition(position: TextEditorPosition): void {
        this.editor.setPosition({ lineNumber: position.getLineNumber(), column: position.getColumn() });
    }

    getSelection(): TextEditorRange {
        const selection = this.editor.getSelection() ?? { startColumn: 1, startLineNumber: 1, endColumn: 1, endLineNumber: 1 };
        return makeTextEditorRange(selection.startLineNumber, selection.startColumn, selection.endLineNumber, selection.endColumn);
    }

    setSelection(selection: TextEditorRange): void {
        const startPosition = selection.getStartPosition();
        const endPosition = selection.getEndPosition();
        this.editor.setSelection({
            startLineNumber: startPosition.getLineNumber(),
            startColumn: startPosition.getColumn(),
            endLineNumber: endPosition.getLineNumber(),
            endColumn: endPosition.getColumn(),
        });
    }

    revealRange(range: TextEditorRange): void {
        const startPosition = range.getStartPosition();
        const endPosition = range.getEndPosition();
        this.editor.revealRangeInCenter({
            startLineNumber: startPosition.getLineNumber(),
            startColumn: startPosition.getColumn(),
            endLineNumber: endPosition.getLineNumber(),
            endColumn: endPosition.getColumn(),
        });
    }
}
