import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

interface ColorArguments {
    color: string;
}

const CLOSE_DELIMITER = '</span>';
export class MonacoColorAction extends MonacoEditorAction {
    static readonly ID = 'monaco-color.action';

    constructor() {
        super(MonacoColorAction.ID, 'artemisApp.multipleChoiceQuestion.editor.color', undefined, undefined);
    }

    executeInCurrentEditor(args?: ColorArguments): void {
        super.executeInCurrentEditor(args);
    }

    run(editor: monaco.editor.ICodeEditor, args?: ColorArguments) {
        const openDelimiter = `<span class="${args?.color ?? 'red'}">`;
        this.toggleDelimiterAroundSelection(editor, openDelimiter, CLOSE_DELIMITER);
        editor.focus();
    }
}
