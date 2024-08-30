import * as monaco from 'monaco-editor';

export type DisposableEditorElement = monaco.IDisposable;
export type MonacoEditorWithActions = monaco.editor.ICodeEditor & { addAction: (action: monaco.editor.IActionDescriptor) => DisposableEditorElement };
export type EditorPosition = monaco.IPosition;
export type MonacoEditorRange = monaco.IRange;
export const KeyModifier = monaco.KeyMod;
export const KeyCode = monaco.KeyCode;
export const CompletionItemKind = monaco.languages.CompletionItemKind;

export function makeEditorPosition(lineNumber: number, column: number): EditorPosition {
    return { lineNumber, column };
}

export function makeEditorRange(startLineNumber: number, startColumn: number, endLineNumber: number, endColumn: number): MonacoEditorRange {
    return { startLineNumber, startColumn, endLineNumber, endColumn };
}
