import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import { MonacoEditorWithActions } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';

export class MonacoCorrectMultipleChoiceAnswerAction extends MonacoEditorDomainAction {
    static readonly ID = 'artemisApp.multipleChoiceQuestion.editor.addCorrectAnswerOption';
    static readonly IDENTIFIER = '[correct]';
    static readonly TEXT = 'Enter a correct answer option here';

    constructor() {
        super(MonacoCorrectMultipleChoiceAnswerAction.ID, 'artemisApp.multipleChoiceQuestion.editor.addCorrectAnswerOption');
    }

    run(editor: MonacoEditorWithActions): void {
        this.addTextWithDomainActionIdentifier(editor, MonacoCorrectMultipleChoiceAnswerAction.TEXT);
    }

    getOpeningIdentifier(): string {
        return MonacoCorrectMultipleChoiceAnswerAction.IDENTIFIER;
    }
}
