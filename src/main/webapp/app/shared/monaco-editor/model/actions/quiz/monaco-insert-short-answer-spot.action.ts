import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import * as monaco from 'monaco-editor';
import { MonacoInsertShortAnswerOptionAction } from 'app/shared/monaco-editor/model/actions/quiz/monaco-insert-short-answer-option.action';

export class MonacoInsertShortAnswerSpotAction extends MonacoEditorAction {
    static readonly ID = 'monaco.insert-short-answer-spot.action';
    spotNumber = 1;

    constructor(readonly insertShortAnswerOptionAction: MonacoInsertShortAnswerOptionAction) {
        super(MonacoInsertShortAnswerSpotAction.ID, 'artemisApp.shortAnswerQuestion.editor.addSpot', undefined);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        // Changes to the editor contents will trigger an update of the spot number. We keep it stored here to use it when inserting the spot.
        const number = this.spotNumber;
        const text = `[-spot ${number}]`;
        const selectedText = this.getSelectedText(editor);
        this.replaceTextAtCurrentSelection(editor, text);
        this.insertShortAnswerOptionAction.executeInCurrentEditor({ spotNumber: number, optionText: selectedText });
    }
}
