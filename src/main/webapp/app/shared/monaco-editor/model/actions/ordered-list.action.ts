import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { faListOl } from '@fortawesome/free-solid-svg-icons';
import { TextEditor } from './adapter/text-editor.interface';
import { TextEditorPosition } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-position.model';
import { makeTextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';

const NUMBER_REGEX = /^\d+\.\s.*/;

/**
 * Action to toggle unordered list in the editor. It toggles the "1. ", "2. ", ... prefix for the entire selection.
 */
export class OrderedListAction extends TextEditorAction {
    static readonly ID = 'ordered-list.action';
    constructor() {
        super(OrderedListAction.ID, 'artemisApp.multipleChoiceQuestion.editor.orderedList', faListOl, undefined);
    }

    /**
     * Toggles the ordered list prefix ("1. ", "2. ", ...) for the entire selection. If the selection is already an ordered list, the prefix is removed from all lines.
     * If no text is selected, the prefix "1. " is inserted at the current cursor position.
     * @param editor The editor in which to toggle the ordered list.
     */
    run(editor: TextEditor): void {
        const selection = editor.getSelection();
        if (!selection) return;

        const startLineNumber = selection.getStartPosition().getLineNumber();
        const endLineNumber = selection.getEndPosition().getLineNumber();

        let isOrderedList = true;
        let allLinesEmpty = true;
        for (let lineNumber = startLineNumber; lineNumber <= endLineNumber; lineNumber++) {
            const lineContent = this.getLineText(editor, lineNumber);
            if (lineContent) {
                allLinesEmpty = false;
                if (!NUMBER_REGEX.test(lineContent)) {
                    isOrderedList = false;
                    break;
                }
            }
        }

        if (allLinesEmpty) {
            this.insertTextAtPosition(editor, new TextEditorPosition(startLineNumber, 1), '1. ');
            // Move the cursor to after the inserted "1. "
            editor.setPosition(new TextEditorPosition(startLineNumber, 1 + '1. '.length));
            editor.focus();
            return;
        }

        for (let lineNumber = startLineNumber; lineNumber <= endLineNumber; lineNumber++) {
            const lineContent = this.getLineText(editor, lineNumber);
            if (!lineContent) continue;

            if (isOrderedList) {
                const idx = lineContent.indexOf('. ');
                if (idx >= 0) this.deleteTextAtRange(editor, makeTextEditorRange(lineNumber, 1, lineNumber, idx + 3));
            } else {
                this.replaceTextAtRange(editor, makeTextEditorRange(lineNumber, 1, lineNumber, 1), `${lineNumber - startLineNumber + 1}. `);
            }
        }
    }
}
