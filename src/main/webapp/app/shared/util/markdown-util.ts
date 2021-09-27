/**
 * adds the passed text into the editor of the passed ace editor component at the current curser by focusing, clearing a selection,
 * moving the cursor to the end of the line, and finally inserting the given text.
 * After that the new test will be selected
 *
 * @param text the text that will be added into the editor of the passed ace editor component
 * @param aceEditor the editor in which the text will be added at the current curser position
 */
export function addTextAtCursor(text: string, aceEditor: any) {
    aceEditor.focus();
    aceEditor.clearSelection();
    aceEditor.moveCursorTo(aceEditor.getCursorPosition().row, Number.POSITIVE_INFINITY);
    aceEditor.insert(text);
    const range = aceEditor.selection.getRange();
    const commandIdentifier = text.split(']');
    const offsetRange = commandIdentifier[0].length + 1;
    range.setStart(range.start.row, offsetRange);
    aceEditor.selection.setRange(range);
}

/**
 * Remove the text at the specified range.
 * @param from = col & row from which to start
 * @param to = col & row at which to end
 * @param aceEditor the editor in which text should be removed
 */
export function removeTextRange(from: { col: number; row: number }, to: { col: number; row: number }, aceEditor: any) {
    aceEditor.focus();
    aceEditor.getSession().remove({ startRow: from.row, startColumn: from.col, endRow: to.row, endColumn: to.col });
}
