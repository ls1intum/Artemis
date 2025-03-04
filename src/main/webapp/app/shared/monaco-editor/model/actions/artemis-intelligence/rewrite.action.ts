import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { WritableSignal } from '@angular/core';
import RewriteResult from 'app/shared/monaco-editor/model/actions/artemis-intelligence/RewriteResult';

/**
 * Artemis Intelligence action for rewriting in the editor.
 */
export class RewriteAction extends TextEditorAction {
    static readonly ID = 'artemisIntelligence.rewrite.action';

    element?: HTMLElement;

    constructor(
        private readonly artemisIntelligenceService: ArtemisIntelligenceService,
        private readonly rewritingVariant: RewritingVariant,
        private readonly courseId: number,
        private readonly resultSignal: WritableSignal<RewriteResult>,
    ) {
        super(RewriteAction.ID, 'artemisApp.markdownEditor.artemisIntelligence.commands.rewrite');
    }

    /**
     * Runs the rewriting of the markdown content of the editor.
     * @param editor The editor in which to rewrite the markdown.
     */
    run(editor: TextEditor): void {
        this.rewriteMarkdown(editor, this.artemisIntelligenceService, this.rewritingVariant, this.courseId, this.resultSignal);
    }
}
