import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import * as monaco from 'monaco-editor';

export class MonacoCorrectMultipleChoiceAnswerAction extends MonacoEditorDomainAction {
    static readonly ID = 'artemisApp.multipleChoiceQuestion.editor.addCorrectAnswerOption';
    static readonly IDENTIFIER = '[correct]';
    static readonly TEXT = 'Enter a correct answer option here';

    constructor() {
        super(MonacoCorrectMultipleChoiceAnswerAction.ID, 'artemisApp.multipleChoiceQuestion.editor.addCorrectAnswerOption');
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.insertTextAtPosition(editor, this.getPosition(editor), `\n${this.getOpeningIdentifier()} ${MonacoCorrectMultipleChoiceAnswerAction.TEXT}`);
        editor.focus();
        this.setPosition(editor, this.getPosition(editor), true);
    }

    getOpeningIdentifier(): string {
        return MonacoCorrectMultipleChoiceAnswerAction.IDENTIFIER;
    }
}
