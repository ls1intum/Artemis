import { AceEditorComponent } from 'ng2-ace-editor';
import * as ace from 'brace';

const Range = ace.acequire('ace/range').Range;

/**
 * adds the passed text into the editor of the passed ace editor component at the current curser by focusing, clearing a selection,
 * moving the cursor to the end of the line, and finally inserting the given text.
 * After that the new test will be selected
 *
 * @param text the text that will be added into the editor of the passed ace editor component
 * @param aceEditorContainer holds the editor in which the text will be added at the current curser position
 */
export function addTextAtCursor(text: String, aceEditorContainer: AceEditorComponent) {
    aceEditorContainer.getEditor().focus();
    aceEditorContainer.getEditor().clearSelection();
    aceEditorContainer.getEditor().moveCursorTo(aceEditorContainer.getEditor().getCursorPosition().row, Number.POSITIVE_INFINITY);
    aceEditorContainer.getEditor().insert(text);
    const range = aceEditorContainer.getEditor().selection.getRange();
    const commandIdentifier = text.split(']');
    const offsetRange = commandIdentifier[0].length + 1;
    range.setStart(range.start.row, offsetRange);
    aceEditorContainer.getEditor().selection.setRange(range);
}

/**
 * Remove the text at the specified range.
 * @param from = col & row from which to start
 * @param to = col & row at which to end
 * @param aceEditorContainer
 */
export function removeTextRange(from: { col: number; row: number }, to: { col: number; row: number }, aceEditorContainer: AceEditorComponent) {
    aceEditorContainer.getEditor().focus();
    aceEditorContainer.getEditor().getSession().remove(new Range(from.row, from.col, to.row, to.col));
}
