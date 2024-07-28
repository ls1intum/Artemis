import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import * as monaco from 'monaco-editor';

interface InsertShortAnswerOptionArgs {
    spotNumber?: number;
    optionText?: string;
}
export class MonacoInsertShortAnswerOptionAction extends MonacoEditorAction {
    static readonly ID = 'monaco.insert-short-answer-option.action';
    static readonly DEFAULT_TEXT = 'Enter an answer option here and ensure the spot number is correct.';
    static readonly DEFAULT_TEXT_SHORT = 'Enter an answer option here.';

    constructor() {
        super(MonacoInsertShortAnswerOptionAction.ID, 'artemisApp.shortAnswerQuestion.editor.addOption', undefined);
        this.id = MonacoInsertShortAnswerOptionAction.ID;
        this.translationKey = 'artemisApp.shortAnswerQuestion.editor.addOption';
    }

    executeInCurrentEditor(args?: InsertShortAnswerOptionArgs) {
        super.executeInCurrentEditor(args);
    }

    run(editor: monaco.editor.ICodeEditor, args?: InsertShortAnswerOptionArgs): void {
        const optionText = args?.optionText || this.getSelectedText(editor) || MonacoInsertShortAnswerOptionAction.DEFAULT_TEXT;
        let insertedText: string;
        if (args?.spotNumber) {
            insertedText = `\n[-option ${args.spotNumber}] ${optionText}`;
        } else {
            insertedText = `\n[-option #] ${optionText}`;
        }
        // Add additional spacing if the last line is not also an option
        if (!this.getLineText(editor, this.getLineCount(editor))?.startsWith('[-option')) {
            insertedText = `\n\n${insertedText}`;
        }
        this.insertTextAtPosition(editor, this.getEndPosition(editor), insertedText);
        editor.focus();
    }
}
