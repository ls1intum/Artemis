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
        if (selection) {
            for (let lineNumber = selection.startLineNumber; lineNumber <= selection.endLineNumber; lineNumber++) {
                const bulletNumber = lineNumber - selection.startLineNumber + 1;
                const lineContent = this.getLineText(editor, lineNumber);
                // TODO: it could make sense not to toggle each line, but to toggle the whole selection.
                // e.g. if all lines are already numbered, remove numbers from all lines. otherwise, add numbers to all lines and fix the numbering
                if (lineContent && NUMBER_REGEX.test(lineContent)) {
                    const offset = 3; // Delete the dot and the space after the number as well. Columns in Monaco are 1-based.
                    this.deleteTextAtRange(editor, new monaco.Range(lineNumber, 1, lineNumber, lineContent.indexOf('. ') + offset));
                } else {
                    this.insertTextAtPosition(editor, new monaco.Position(lineNumber, 1), `${bulletNumber}. `);
                }
            }
        }
    }
}
