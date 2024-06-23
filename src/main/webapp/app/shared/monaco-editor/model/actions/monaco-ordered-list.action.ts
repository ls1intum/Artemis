import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { faListOl } from '@fortawesome/free-solid-svg-icons';

const NUMBER_REGEX = /^\d+\.\s.*/;
export class MonacoOrderedListAction extends MonacoEditorAction {
    static readonly ID = 'monaco-ordered-list.action';
    constructor() {
        super(MonacoOrderedListAction.ID, 'artemisApp.multipleChoiceQuestion.editor.orderedList', faListOl, undefined);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        const selection = editor.getSelection();
        if (!selection) return;

        let isOrderedList = true;
        for (let lineNumber = selection.startLineNumber; lineNumber <= selection.endLineNumber; lineNumber++) {
            const lineContent = this.getLineText(editor, lineNumber);
            if (lineContent && !NUMBER_REGEX.test(lineContent)) {
                isOrderedList = false;
                break;
            }
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
