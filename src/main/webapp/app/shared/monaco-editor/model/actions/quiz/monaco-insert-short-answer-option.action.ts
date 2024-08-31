import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

interface InsertShortAnswerOptionArgs {
    spotNumber?: number;
    optionText?: string;
}

/**
 * Action to insert a short answer option ([-option #] Option text) at the end of the editor.
 */
export class MonacoInsertShortAnswerOptionAction extends MonacoEditorAction {
    static readonly ID = 'monaco-insert-short-answer-option.action';
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

    /**
     * Inserts a short answer option at the end of the editor. This option has the format [-option #] Option text, where # is the spot number.
     * @param editor The editor to insert the option in.
     * @param args The optional arguments for the action. Can include a spot number (will be # otherwise) and the option text (if blank/absent, the default text will be used).
     */
    run(editor: TextEditor, args?: InsertShortAnswerOptionArgs): void {
        // Note that even if the optionText is provided, it may be blank. This is why we use || instead of ?? here.
        const optionText = args?.optionText || MonacoInsertShortAnswerOptionAction.DEFAULT_TEXT;
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
        // For convenience, we want to select the option text if it is the default text
        if (optionText === MonacoInsertShortAnswerOptionAction.DEFAULT_TEXT) {
            const newEndPosition = this.getEndPosition(editor);
            const selection = {
                startLineNumber: newEndPosition.lineNumber,
                startColumn: newEndPosition.column - MonacoInsertShortAnswerOptionAction.DEFAULT_TEXT.length,
                endLineNumber: newEndPosition.lineNumber,
                endColumn: newEndPosition.column,
            };
            this.setSelection(editor, selection);
        }
        editor.focus();
    }
}
