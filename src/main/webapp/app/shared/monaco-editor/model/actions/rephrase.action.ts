import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';

import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { RephraseService } from 'app/shared/monaco-editor/rephrase.service';
import RephrasingVariant from 'app/shared/monaco-editor/model/rephrasing-variant';
import { facArtemisIntelligence } from 'app/icons/icons';

/**
 * Action to the rephrasing action the editor.
 */
export class RephraseAction extends TextEditorAction {
    static readonly ID = 'rephrase.action';

    element?: HTMLElement;

    constructor(
        private readonly rephraseService: RephraseService,
        private readonly rephrasingVariant: RephrasingVariant,
        private readonly courseId: number,
    ) {
        super(RephraseAction.ID, 'artemisApp.markdownEditor.commands.rephrase', facArtemisIntelligence);
    }

    /**
     * Toggles the rephrasing of the markdown content the editor.
     * @param editor The editor in which to rephrase the markdown.
     * @param variant The variant of the rephrasing.
     */
    run(editor: TextEditor): void {
        this.rephraseMarkdown(editor, this.rephrasingVariant, this.rephraseService, this.courseId);
    }
}
