import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';

import { faReceipt } from '@fortawesome/free-solid-svg-icons';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

/**
 * Artemis Intelligence action to rephrase the current content of the editor.
 */
export class AIRephraseAction extends TextEditorAction {
    static readonly ID = 'rephrase.action';

    element?: HTMLElement;

    constructor() {
        super(AIRephraseAction.ID, 'artemisApp.markdownEditor.commands.rephrase', faReceipt);
    }

    /**
     * Runs the rephrasing action for the given editor.
     * @param editor the editor to run the action on
     */
    run(editor: TextEditor): void {
        // this.rephraseMarkdown(editor, this.rephrasingVariant, this.rephraseService, this.courseId);
    }
}
