import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';

import { faReceipt } from '@fortawesome/free-solid-svg-icons';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import RephrasingVariant, { RephraseService } from 'app/shared/monaco-editor/rephrase.service';

/**
 * Action to toggle fullscreen mode in the editor.
 */
export class RephraseAction extends TextEditorAction {
    static readonly ID = 'rephrase.action';

    element?: HTMLElement;

    constructor(
        private readonly rephraseService: RephraseService,
        private readonly rephrasingVariant: RephrasingVariant,
    ) {
        super(RephraseAction.ID, 'artemisApp.markdownEditor.commands.rephrase', faReceipt);
    }

    /**
     * Toggles the fullscreen mode of the editor.
     * @param editor The editor in which to rephrase the markdown.
     * @param variant The variant of the rephrasing.
     */
    run(editor: TextEditor): void {
        this.rephraseMarkdown(editor, this.rephrasingVariant, this.rephraseService);
    }
}
