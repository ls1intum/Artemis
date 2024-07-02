import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { faListOl } from '@fortawesome/free-solid-svg-icons';

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
    run(editor: monaco.editor.ICodeEditor): void {
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
            this.insertTextAtPosition(editor, new monaco.Position(selection.startLineNumber, 1), '1. ');
            editor.setPosition(new monaco.Position(selection.startLineNumber, 1 + 3));
            editor.focus();
            return;
        }

        for (let lineNumber = selection.startLineNumber; lineNumber <= selection.endLineNumber; lineNumber++) {
            const lineContent = this.getLineText(editor, lineNumber);
            if (!lineContent) continue;

            if (isOrderedList) {
                const idx = lineContent.indexOf('. ');
                if (idx >= 0) this.deleteTextAtRange(editor, new monaco.Range(lineNumber, 1, lineNumber, idx + 3));
            } else {
                this.replaceTextAtRange(editor, new monaco.Range(lineNumber, 1, lineNumber, 1), `${lineNumber - selection.startLineNumber + 1}. `);
            }
        }
    }
}
