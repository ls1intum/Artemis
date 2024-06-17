import { MonacoEditorDelimiterAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-delimiter-action.model';

import * as monaco from 'monaco-editor';

const OPEN_DELIMITER = '<span class="red">';
const CLOSE_DELIMITER = '</span>';
export class MonacoColorAction extends MonacoEditorDelimiterAction {
    static readonly ID = 'monaco-color.action';

    possibleValues = ['red', 'green', 'white', 'black', 'blue', 'lila', 'orange', 'yellow'];

    constructor() {
        super(MonacoColorAction.ID, 'artemisApp.multipleChoiceQuestion.editor.color', undefined, undefined, OPEN_DELIMITER, CLOSE_DELIMITER);
    }

    run(editor: monaco.editor.ICodeEditor, color: string = 'red') {
        const defaultOpenDelimiter = this.openDelimiter;
        this.openDelimiter = `<span class="${color}">`;
        super.run(editor);
        this.openDelimiter = defaultOpenDelimiter;
    }
}
