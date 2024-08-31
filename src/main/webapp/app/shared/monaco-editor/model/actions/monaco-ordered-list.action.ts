import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { faListOl } from '@fortawesome/free-solid-svg-icons';
import { TextEditor } from './adapter/text-editor-adapter.model';

const NUMBER_REGEX = /^\d+\.\s.*/;

/**
 * Action to toggle unordered list in the editor. It toggles the "1. ", "2. ", ... prefix for the entire selection.
 */
export class MonacoOrderedListAction extends MonacoEditorAction {
    static readonly ID = 'monaco-ordered-list.action';
    constructor() {
        super(MonacoOrderedListAction.ID, 'artemisApp.multipleChoiceQuestion.editor.orderedList', faListOl, undefined);
    }

    /**
     * Toggles the ordered list prefix ("1. ", "2. ", ...) for the entire selection. If the selection is already an ordered list, the prefix is removed from all lines.
     * If no text is selected, the prefix "1. " is inserted at the current cursor position.
     * @param editor The editor in which to toggle the ordered list.
     */
    run(editor: TextEditor): void {
        const selection = editor.getSelection();
        if (!selection) return;

        let isOrderedList = true;
        let allLinesEmpty = true;
        for (let lineNumber = selection.startLineNumber; lineNumber <= selection.endLineNumber; lineNumber++) {
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
            this.insertTextAtPosition(editor, { lineNumber: selection.startLineNumber, column: 1 }, '1. ');
            // Move the cursor to after the inserted "1. "
            editor.setPosition({ lineNumber: selection.startLineNumber, column: 4 });
            editor.focus();
            return;
        }

        for (let lineNumber = selection.startLineNumber; lineNumber <= selection.endLineNumber; lineNumber++) {
            const lineContent = this.getLineText(editor, lineNumber);
            if (!lineContent) continue;

            if (isOrderedList) {
                const idx = lineContent.indexOf('. ');
                if (idx >= 0) this.deleteTextAtRange(editor, { startLineNumber: lineNumber, startColumn: 1, endLineNumber: lineNumber, endColumn: idx + 3 });
            } else {
                this.replaceTextAtRange(
                    editor,
                    { startLineNumber: lineNumber, startColumn: 1, endLineNumber: lineNumber, endColumn: 1 },
                    `${lineNumber - selection.startLineNumber + 1}. `,
                );
            }
        }
    }
}
