import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

import * as monaco from 'monaco-editor';
import { faCompress } from '@fortawesome/free-solid-svg-icons';

export class MonacoFullscreenAction extends MonacoEditorAction {
    static readonly ID = 'monaco-fullscreen.action';

    element?: HTMLElement;

    constructor() {
        super(MonacoFullscreenAction.ID, 'artemisApp.markdownEditor.commands.fullscreen', faCompress);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.toggleFullscreen(editor, this.element);
    }
}
