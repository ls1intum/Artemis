import { TextEditorPosition } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-position.model';

/**
 * Represents a range in a text editor consisting of 1-based line numbers and columns.
 */
export class TextEditorRange {
    /**
     * Creates a new text editor range.
     * @param startPosition The position at which this range starts.
     * @param endPosition The position at which this range ends.
     */
    constructor(
        private readonly startPosition: TextEditorPosition,
        private readonly endPosition: TextEditorPosition,
    ) {}

    getStartPosition(): TextEditorPosition {
        return this.startPosition;
    }

    getEndPosition(): TextEditorPosition {
        return this.endPosition;
    }
}

/**
 * Creates a new text editor range. Line numbers and columns start at 1.
 * @param startLineNumber The line number of the start of the range.
 * @param startColumn The column of the start of the range.
 * @param endLineNumber The line number of the end of the range.
 * @param endColumn The column of the end of the range.
 */
export function makeTextEditorRange(startLineNumber: number, startColumn: number, endLineNumber: number, endColumn: number): TextEditorRange {
    return new TextEditorRange(new TextEditorPosition(startLineNumber, startColumn), new TextEditorPosition(endLineNumber, endColumn));
}
