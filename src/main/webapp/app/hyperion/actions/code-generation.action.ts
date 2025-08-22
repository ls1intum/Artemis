import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

/**
 * Artemis Intelligence action for code generation in programming exercises
 */
export class CodeGenerationAction extends TextEditorAction {
    static readonly ID = 'artemisIntelligence.codeGeneration.action';

    constructor(private readonly executeCallback: () => void) {
        super(CodeGenerationAction.ID, 'artemisApp.programmingExercise.codeGeneration.generateCode');
    }

    /**
     * Runs the code generation action
     * @param editor The editor (not used for code generation)
     */
    run(editor: TextEditor): void {
        this.executeCallback();
    }

    /**
     * Execute the code generation in the current context
     */
    executeInCurrentEditor(): void {
        this.executeCallback();
    }
}
