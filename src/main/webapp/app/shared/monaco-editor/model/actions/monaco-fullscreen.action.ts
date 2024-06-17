import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

import * as monaco from 'monaco-editor';
import { faCompress } from '@fortawesome/free-solid-svg-icons';
import { enterFullscreen, exitFullscreen, isFullScreen } from 'app/shared/util/fullscreen.util';

export class MonacoFullscreenAction extends MonacoEditorAction {
    static readonly ID = 'monaco-fullscreen.action';

    element?: HTMLElement;

    constructor(translationKey: string) {
        super(MonacoFullscreenAction.ID, translationKey, faCompress);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        const element = this.element ?? editor.getDomNode();
        if (isFullScreen()) {
            exitFullscreen();
            editor.layout();
        } else if (element) {
            enterFullscreen(element);
            editor.layout();
        }
    }
}
