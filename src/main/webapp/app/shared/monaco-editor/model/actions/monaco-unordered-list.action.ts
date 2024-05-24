import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { faListUl } from '@fortawesome/free-solid-svg-icons';

const LIST_BULLET = '- ';
export class MonacoUnorderedListAction extends MonacoEditorAction {
    constructor(label: string, translationKey: string) {
        super('monaco-unordered-list.action', label, translationKey, faListUl, undefined);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        const selection = editor.getSelection();
        if (selection) {
            for (let lineNumber = selection.startLineNumber; lineNumber <= selection.endLineNumber; lineNumber++) {
                const lineContent = this.getLineText(editor, lineNumber);
                // TODO: it could make sense not to toggle each line, but to toggle the whole selection.
                // e.g. if all lines are already bulleted, remove bullets from all lines. otherwise, add bullets to all lines.
                if (lineContent?.startsWith(LIST_BULLET)) {
                    this.deleteTextAtRange(editor, new monaco.Range(lineNumber, 1, lineNumber, 1 + LIST_BULLET.length));
                } else {
                    this.insertTextAtPosition(editor, new monaco.Position(lineNumber, 1), LIST_BULLET);
                }
            }
        }
    }
}
