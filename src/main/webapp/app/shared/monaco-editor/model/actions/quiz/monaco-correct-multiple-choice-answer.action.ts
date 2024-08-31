import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-adapter.model';

export class MonacoCorrectMultipleChoiceAnswerAction extends MonacoEditorDomainAction {
    static readonly ID = 'artemisApp.multipleChoiceQuestion.editor.addCorrectAnswerOption';
    static readonly IDENTIFIER = '[correct]';
    static readonly TEXT = 'Enter a correct answer option here';

    constructor() {
        super(MonacoCorrectMultipleChoiceAnswerAction.ID, 'artemisApp.multipleChoiceQuestion.editor.addCorrectAnswerOption');
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, MonacoCorrectMultipleChoiceAnswerAction.TEXT);
    }

    getOpeningIdentifier(): string {
        return MonacoCorrectMultipleChoiceAnswerAction.IDENTIFIER;
    }
}
