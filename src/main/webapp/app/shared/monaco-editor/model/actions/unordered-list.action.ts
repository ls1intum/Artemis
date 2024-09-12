import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { faListUl } from '@fortawesome/free-solid-svg-icons';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorPosition } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-position.model';
import { makeTextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';

const LIST_BULLET = '- ';

/**
 * Action to toggle unordered list in the editor. It toggles the "- " prefix for the entire selection.
 */
export class UnorderedListAction extends TextEditorAction {
    static readonly ID = 'unordered-list.action';
    constructor() {
        super(UnorderedListAction.ID, 'artemisApp.multipleChoiceQuestion.editor.unorderedList', faListUl, undefined);
    }

    /**
     * Toggles the unordered list prefix ("- ") for the entire selection. If the selection is already an unordered list, the prefix is removed from all lines.
     * If no text is selected, the prefix is inserted at the current cursor position.
     * @param editor The editor in which to toggle the unordered list.
     */
    run(editor: TextEditor): void {
        const selection = editor.getSelection();
        if (!selection) return;

        const startLineNumber = selection.getStartPosition().getLineNumber();
        const endLineNumber = selection.getEndPosition().getLineNumber();

        let isUnorderedList = true;
        let allLinesEmpty = true;
        for (let lineNumber = startLineNumber; lineNumber <= endLineNumber; lineNumber++) {
            const lineContent = this.getLineText(editor, lineNumber);
            if (lineContent) {
                allLinesEmpty = false;
                if (!lineContent.startsWith(LIST_BULLET)) {
                    isUnorderedList = false;
                    break;
                }
            }
        }

        if (allLinesEmpty) {
            this.insertTextAtPosition(editor, new TextEditorPosition(startLineNumber, 1), LIST_BULLET);
            editor.setPosition(new TextEditorPosition(startLineNumber, 1 + LIST_BULLET.length));
            editor.focus();
            return;
        }

        for (let lineNumber = startLineNumber; lineNumber <= endLineNumber; lineNumber++) {
            const lineContent = this.getLineText(editor, lineNumber);
            if (!lineContent) continue;

            if (isUnorderedList) {
                this.deleteTextAtRange(editor, makeTextEditorRange(lineNumber, 1, lineNumber, 1 + LIST_BULLET.length));
            } else {
                this.insertTextAtPosition(editor, new TextEditorPosition(lineNumber, 1), LIST_BULLET);
            }
        }
    }
}
