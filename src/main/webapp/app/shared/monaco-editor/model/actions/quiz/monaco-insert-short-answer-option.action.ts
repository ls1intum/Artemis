import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import * as monaco from 'monaco-editor';

export class MonacoInsertShortAnswerOptionAction extends MonacoEditorAction {
    static readonly ID = 'monaco.insert-short-answer-option.action';

    constructor() {
        super(MonacoInsertShortAnswerOptionAction.ID, 'artemisApp.shortAnswerQuestion.editor.addOption', undefined);
        this.id = MonacoInsertShortAnswerOptionAction.ID;
        this.translationKey = 'artemisApp.shortAnswerQuestion.editor.addOption';
    }

    run(editor: monaco.editor.ICodeEditor, args?: { spotNumber: number; optionText: string }): void {
        // TODO: Localize this text
        alert('Insert opt' + JSON.stringify(args));
        const text = args?.optionText || this.getSelectedText(editor) || 'Enter an answer option here and ensure the spot number is correct.';
        let snippet;
        if (args?.spotNumber) {
            snippet = `\n[-option ${args.spotNumber}] \${1:${text}}`;
        } else {
            snippet = `\n[-option \${1:#}] \${2:${text}}`;
        }
        // Add additional spacing if the last line is not also an option
        if (!this.getLineText(editor, this.getLineCount(editor))?.startsWith('[-option')) {
            snippet = `\n\n${snippet}`;
        }
        this.insertSnippetAtPosition(editor, this.getEndPosition(editor), snippet);
        editor.focus();
    }
}
