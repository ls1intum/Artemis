import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

import * as monaco from 'monaco-editor';
import { faCompress } from '@fortawesome/free-solid-svg-icons';
import { ElementRef } from '@angular/core';

export class MonacoFullscreenAction extends MonacoEditorAction {
    static readonly ID = 'monaco-fullscreen.action';

    constructor(label: string, translationKey: string) {
        super(MonacoFullscreenAction.ID, label, translationKey, faCompress);
    }

    run(editor: monaco.editor.ICodeEditor, element?: ElementRef): void {
        const elementNode: HTMLElement = element?.nativeElement ?? editor.getContainerDomNode();
        if (document.fullscreenElement) {
            document.exitFullscreen().then(() => editor.layout());
        } else {
            elementNode.requestFullscreen().then(() => editor.layout());
        }
    }
}
