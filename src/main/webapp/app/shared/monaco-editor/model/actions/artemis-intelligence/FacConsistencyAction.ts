import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { WritableSignal } from '@angular/core';
import { ConsistencyCheckResult } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence-results';

/**
 * Artemis Intelligence action for rewriting in the editor.
 */
export class FaqConsistencyAction extends TextEditorAction {
    static readonly ID = 'artemisIntelligence.faqConsistency.action';

    element?: HTMLElement;

    constructor(
        private readonly artemisIntelligenceService: ArtemisIntelligenceService,
        private readonly courseId: number,
        private readonly resultSignal: WritableSignal<ConsistencyCheckResult>,
    ) {
        super(FaqConsistencyAction.ID, 'artemisApp.markdownEditor.artemisIntelligence.commands.consistencyCheck');
    }

    /**
     * Runs the rewriting of the markdown content of the editor.
     * @param editor The editor in which to rewrite the markdown.
     */
    run(editor: TextEditor): void {
        this.checkForFaqConsistency(editor, this.artemisIntelligenceService, this.courseId, this.resultSignal);
    }
}
