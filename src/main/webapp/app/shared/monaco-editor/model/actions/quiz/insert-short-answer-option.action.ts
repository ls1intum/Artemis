import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { makeTextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';

interface InsertShortAnswerOptionArgs {
    spotNumber?: number;
    optionText?: string;
}

/**
 * Action to insert a short answer option ([-option #] Option text) at the end of the editor.
 */
export class InsertShortAnswerOptionAction extends TextEditorAction {
    static readonly ID = 'insert-short-answer-option.action';
    static readonly DEFAULT_TEXT = 'Enter an answer option here and ensure the spot number is correct.';
    static readonly DEFAULT_TEXT_SHORT = 'Enter an answer option here.';

    constructor() {
        super(InsertShortAnswerOptionAction.ID, 'artemisApp.shortAnswerQuestion.editor.addOption', undefined);
        this.id = InsertShortAnswerOptionAction.ID;
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
        const optionText = args?.optionText || InsertShortAnswerOptionAction.DEFAULT_TEXT;
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
        if (optionText === InsertShortAnswerOptionAction.DEFAULT_TEXT) {
            const newEndPosition = this.getEndPosition(editor);
            const selection = makeTextEditorRange(
                newEndPosition.getLineNumber(),
                newEndPosition.getColumn() - InsertShortAnswerOptionAction.DEFAULT_TEXT.length,
                newEndPosition.getLineNumber(),
                newEndPosition.getColumn(),
            );
            this.setSelection(editor, selection);
        }
        editor.focus();
    }
}
