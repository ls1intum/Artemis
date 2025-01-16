import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { RewritingService } from 'app/shared/monaco-editor/rewriting.service';
import RewritingVariant from 'app/shared/monaco-editor/model/rewriting-variant';
import { facArtemisIntelligence } from 'app/icons/icons';

/**
 * Action to the rewriting action the editor.
 */
export class RewriteAction extends TextEditorAction {
    static readonly ID = 'rewrite.action';

    element?: HTMLElement;

    constructor(
        private readonly rewriteService: RewritingService,
        private readonly rewritingVariant: RewritingVariant,
        private readonly courseId: number,
    ) {
        super(RewriteAction.ID, 'artemisApp.markdownEditor.commands.rewrite', facArtemisIntelligence);
    }

    /**
     * Toggles the rephrrewritingasing of the markdown content the editor.
     * @param editor The editor in which to rewrite the markdown.
     * @param variant The variant of the rewritingVariant.
     */
    run(editor: TextEditor): void {
        this.rewriteMarkdown(editor, this.rewritingVariant, this.rewriteService, this.courseId);
    }
}
