import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { WritableSignal } from '@angular/core';

/**
 * Artemis Intelligence action for consistency checking exercises
 */
export class ConsistencyCheckAction extends TextEditorAction {
    static readonly ID = 'artemisIntelligence.consistencyCheck.action';

    element?: HTMLElement;

    constructor(
        private readonly artemisIntelligenceService: ArtemisIntelligenceService,
        private readonly exerciseId: number,
        private readonly resultSignal: WritableSignal<string>,
    ) {
        super(ConsistencyCheckAction.ID, 'artemisApp.markdownEditor.artemisIntelligence.commands.consistencyCheck');
    }

    /**
     * Runs the consistency check on the exercise.
     *
     * @param editor The editor in which to rewrite the markdown.
     * @param artemisIntelligenceService The service to use for rewriting the markdown.
     * @param exerciseId The id of the exercise to check.
     * @param resultSignal The signal to write the result of the consistency check to.
     */
    run(editor: TextEditor): void {
        this.consistencyCheck(editor, this.artemisIntelligenceService, this.exerciseId, this.resultSignal);
    }
}
