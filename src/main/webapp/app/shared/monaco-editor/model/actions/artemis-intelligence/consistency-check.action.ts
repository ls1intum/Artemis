import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';

/**
 * Artemis Intelligence action for consistency checking exercises
 */
export class ConsistencyCheckAction extends TextEditorAction {
    static readonly ID = 'artemisIntelligence.consistencyCheck.action';

    element?: HTMLElement;

    constructor(
        private readonly artemisIntelligenceService: ArtemisIntelligenceService,
        private readonly courseId: number,
        private readonly exerciseId: number,
    ) {
        super(ConsistencyCheckAction.ID, 'artemisApp.markdownEditor.artemisIntelligence.commands.consistencyCheck');
    }

    /**
     * Runs the rewriting of the markdown content of the editor.
     * @param editor The editor in which to rewrite the markdown.
     */
    run(editor: TextEditor): void {
        this.consistencyCheck(editor, this.artemisIntelligenceService, this.courseId, this.exerciseId);
    }
}
