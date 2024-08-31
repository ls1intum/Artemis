import * as monaco from 'monaco-editor';
// Generic Monaco editor types
export type Disposable = { dispose(): void };
export type MonacoEditorWithActions = monaco.editor.ICodeEditor & { addAction: (action: monaco.editor.IActionDescriptor) => Disposable };
export type MonacoEditorTextModel = monaco.editor.ITextModel;
export type EditorPosition = monaco.IPosition;
export type EditorRange = monaco.IRange;
export type EditorOptions = monaco.editor.IEditorOptions;
// Enums
export const KeyModifier = monaco.KeyMod;
export const KeyCode = monaco.KeyCode;

export function makeEditorRange(startLineNumber: number, startColumn: number, endLineNumber: number, endColumn: number): EditorRange {
    return { startLineNumber, startColumn, endLineNumber, endColumn };
}
