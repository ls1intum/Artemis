import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { MonacoInsertShortAnswerOptionAction } from 'app/shared/monaco-editor/model/actions/quiz/monaco-insert-short-answer-option.action';
import { MonacoEditorWithActions } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';

/**
 * Action to insert a short answer spot at the current cursor position.
 * After inserting the spot, this action also inserts an option linked to the spot.
 */
export class MonacoInsertShortAnswerSpotAction extends MonacoEditorAction {
    static readonly ID = 'monaco-insert-short-answer-spot.action';
    spotNumber = 1;

    /**
     * @param insertShortAnswerOptionAction The action to insert a short answer option. This action will be executed after inserting the spot. Must be registered in the same editor.
     */
    constructor(readonly insertShortAnswerOptionAction: MonacoInsertShortAnswerOptionAction) {
        super(MonacoInsertShortAnswerSpotAction.ID, 'artemisApp.shortAnswerQuestion.editor.addSpot', undefined);
    }

    /**
     * Inserts a spot at the current cursor position (if no text is selected) or replaces the selected text with a spot.
     * Then, it inserts an option linked to the spot. If the selected text is not empty, it will be used as the option's text.
     * @param editor The editor to insert the spot in.
     */
    run(editor: MonacoEditorWithActions): void {
        // Changes to the editor contents will trigger an update of the spot number. We keep it stored here to use it when inserting the spot.
        const number = this.spotNumber;
        const text = `[-spot ${number}]`;
        const selectedText = this.getSelectedText(editor);
        this.replaceTextAtCurrentSelection(editor, text);
        this.insertShortAnswerOptionAction.executeInCurrentEditor({ spotNumber: number, optionText: selectedText });
    }
}
