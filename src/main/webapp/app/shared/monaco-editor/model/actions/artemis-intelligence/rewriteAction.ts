import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import RewritingVariant from 'app/shared/monaco-editor/model/rewriting-variant';
import { RewritingService } from 'app/shared/monaco-editor/rewriting.service';

/**
 * Artemis Intelligence action for rewriting in the editor.
 */
export class RewriteAction extends TextEditorAction {
    static readonly ID = 'artemisIntelligence.rewrite.action';

    element?: HTMLElement;

    constructor(
        private readonly rewriteService: RewritingService,
        private readonly rewritingVariant: RewritingVariant,
        private readonly courseId: number,
    ) {
        super(RewriteAction.ID, 'artemisApp.markdownEditor.artemisIntelligence.commands.rewrite');
    }

    /**
     * Runs the rewriting of the markdown content of the editor.
     * @param editor The editor in which to rewrite the markdown.
     */
    run(editor: TextEditor): void {
        this.rewriteMarkdown(editor, this.rewriteService, this.rewritingVariant, this.courseId);
    }
}
