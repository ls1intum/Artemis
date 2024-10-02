/**
 * Represents a position in a text editor consisting of 1-based line numbers and columns.
 */
export class TextEditorPosition {
    constructor(
        private readonly lineNumber: number,
        private readonly column: number,
    ) {}

    /**
     * Creates a copy of this position with the given column.
     * @param column The column of the new position.
     */
    withColumn(column: number): TextEditorPosition {
        return new TextEditorPosition(this.lineNumber, column);
    }

    getLineNumber(): number {
        return this.lineNumber;
    }

    getColumn(): number {
        return this.column;
    }
}

/**
 * Creates a new text editor position from the given position, shifted by the given number of lines and columns.
 * @param position The position to shift.
 * @param lines The number of lines to shift the position by.
 * @param columns The number of columns to shift the position by.
 */
export function shiftPosition(position: TextEditorPosition, lines: number, columns: number): TextEditorPosition {
    return new TextEditorPosition(position.getLineNumber() + lines, position.getColumn() + columns);
}
