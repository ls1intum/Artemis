import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import * as monaco from 'monaco-editor';

export class MonacoInsertShortAnswerOptionAction extends MonacoEditorAction {
    static readonly ID = 'monaco.insert-short-answer-option.action';

    constructor() {
        this.id = MonacoInsertShortAnswerOptionAction.ID;
        this.translationKey = 'artemisApp.shortAnswerQuestion.editor.addOption';
    }

    run(editor: monaco.editor.ICodeEditor, args?: { optionNumber: number }): void {
        // TODO: Localize this text
        let snippet;
        if (args?.optionNumber) {
            snippet = `\n[-option ${args.optionNumber}] \${1:Enter an answer option here.}`;
        } else {
            snippet = `\n[-option \${2:#}] \${1:Enter an answer option here. Do not forget to set the number of the spot.}`;
        }
        // Add additional spacing if the last line is not also an option
        if (!this.getLineText(editor, this.getLineCount(editor))?.startsWith('[-option')) {
            snippet = `\n\n${snippet}`;
        }
        this.insertSnippetAtPosition(editor, snippet, this.getEndPosition());
        editor.focus();
    }
}
