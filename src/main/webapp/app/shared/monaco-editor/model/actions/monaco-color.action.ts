import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

const CLOSE_DELIMITER = '</span>';
export class MonacoColorAction extends MonacoEditorAction {
    static readonly ID = 'monaco-color.action';

    constructor() {
        super(MonacoColorAction.ID, 'artemisApp.multipleChoiceQuestion.editor.color', undefined, undefined);
    }

    run(editor: monaco.editor.ICodeEditor, color: string = 'red') {
        const openDelimiter = `<span class="${color}">`;
        this.toggleDelimiterAroundSelection(editor, openDelimiter, CLOSE_DELIMITER);
    }
}
