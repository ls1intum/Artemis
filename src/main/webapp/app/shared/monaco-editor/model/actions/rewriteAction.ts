import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import RewritingVariant from 'app/shared/monaco-editor/model/rewriting-variant';
import { facArtemisIntelligence } from 'app/icons/icons';

/**
 * Action to the rewriting action the editor.
 */
export class RewriteAction extends TextEditorAction {
    static readonly ID = 'rewrite.action';

    element?: HTMLElement;

    constructor(
        private readonly rewritingVariant: RewritingVariant,
        private readonly courseId: number,
    ) {
        super(RewriteAction.ID, 'artemisApp.markdownEditor.commands.rewrite', facArtemisIntelligence);
    }

    /**
     * Runs the rewriting of the markdown content of the editor.
     * @param editor The editor in which to rewrite the markdown.
     */
    run(editor: TextEditor): void {
        this.rewriteMarkdown(editor, this.rewritingVariant, this.courseId);
    }
}
