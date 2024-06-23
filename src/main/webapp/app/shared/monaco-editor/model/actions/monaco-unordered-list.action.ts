import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { faListUl } from '@fortawesome/free-solid-svg-icons';

const LIST_BULLET = '- ';
export class MonacoUnorderedListAction extends MonacoEditorAction {
    static readonly ID = 'monaco-unordered-list.action';
    constructor() {
        super(MonacoUnorderedListAction.ID, 'artemisApp.multipleChoiceQuestion.editor.unorderedList', faListUl, undefined);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        const selection = editor.getSelection();
        if (!selection) return;

        let isUnorderedList = true;
        let allLinesEmpty = true;
        for (let lineNumber = selection.startLineNumber; lineNumber <= selection.endLineNumber; lineNumber++) {
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
            this.insertTextAtPosition(editor, new monaco.Position(selection.startLineNumber, 1), LIST_BULLET);
            editor.setPosition(new monaco.Position(selection.startLineNumber, 1 + LIST_BULLET.length));
            editor.focus();
            return;
        }

        for (let lineNumber = selection.startLineNumber; lineNumber <= selection.endLineNumber; lineNumber++) {
            const lineContent = this.getLineText(editor, lineNumber);
            if (!lineContent) continue;

            if (isUnorderedList) {
                this.deleteTextAtRange(editor, new monaco.Range(lineNumber, 1, lineNumber, 1 + LIST_BULLET.length));
            } else {
                this.insertTextAtPosition(editor, new monaco.Position(lineNumber, 1), LIST_BULLET);
            }
        }
    }
}
