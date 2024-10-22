import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';

import { faCompress } from '@fortawesome/free-solid-svg-icons';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

/**
 * Action to toggle fullscreen mode in the editor.
 */
export class FullscreenAction extends TextEditorAction {
    static readonly ID = 'fullscreen.action';

    element?: HTMLElement;

    constructor() {
        super(FullscreenAction.ID, 'artemisApp.markdownEditor.commands.fullscreen', faCompress);
    }

    /**
     * Toggles the fullscreen mode of the editor.
     * @param editor The editor in which to toggle fullscreen mode.
     */
    run(editor: TextEditor): void {
        this.toggleFullscreen(editor, this.element);
    }
}
