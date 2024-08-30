import * as monaco from 'monaco-editor';

/*
 * This file contains type definitions and utility functions for the Monaco editor, which reduces the amount of imports needed in other files.
 */

// Generic Monaco editor types
export type DisposableEditorElement = monaco.IDisposable;
export type MonacoEditorWithActions = monaco.editor.ICodeEditor & { addAction: (action: monaco.editor.IActionDescriptor) => DisposableEditorElement };
export type EditorPosition = monaco.IPosition;
export type EditorRange = monaco.IRange;
export type MonacoEditorOptions = monaco.editor.IEditorOptions;
export type EditorMouseEvent = monaco.editor.IEditorMouseEvent;
// Types for elements in the editor
export type DecorationsCollection = monaco.editor.IEditorDecorationsCollection;
export type DeltaDecoration = monaco.editor.IModelDeltaDecoration;
export type GlyphMarginWidget = monaco.editor.IGlyphMarginWidget;
export type GlyphMarginLane = monaco.editor.GlyphMarginLane;
export type GlyphMarginPosition = monaco.editor.IGlyphMarginWidgetPosition;
export type OverlayWidget = monaco.editor.IOverlayWidget;
export type OverlayWidgetPosition = monaco.editor.IOverlayWidgetPosition | null; // null is used by the monaco editor API
export type ViewZone = monaco.editor.IViewZone;
// Enums
export const GlyphMarginLane = monaco.editor.GlyphMarginLane;
export const TrackedRangeStickiness = monaco.editor.TrackedRangeStickiness;
export const KeyModifier = monaco.KeyMod;
export const KeyCode = monaco.KeyCode;
export const CompletionItemKind = monaco.languages.CompletionItemKind;

export function makeEditorPosition(lineNumber: number, column: number): EditorPosition {
    return { lineNumber, column };
}

export function makeEditorRange(startLineNumber: number, startColumn: number, endLineNumber: number, endColumn: number): EditorRange {
    return { startLineNumber, startColumn, endLineNumber, endColumn };
}
