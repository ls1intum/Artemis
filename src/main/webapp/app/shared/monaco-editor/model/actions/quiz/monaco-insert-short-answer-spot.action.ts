import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import * as monaco from 'monaco-editor';

export class MonacoInsertShortAnswerSpot extends MonacoEditorAction {
    static readonly ID = 'monaco.insert-short-answer-spot.action';

    constructor() {
        super(MonacoInsertShortAnswerSpot.ID, 'artemisApp.shortAnswerQuestion.editor.addSpot', undefined);
    }

    run(editor: monaco.editor.ICodeEditor, args?: { spotNumber: number }): void {
        const number = args?.spotNumber ?? 1;
        const text = `[-spot ${number}]`;
        this.replaceTextAtCurrentSelection(editor, text);
        editor.focus();
    }
}
