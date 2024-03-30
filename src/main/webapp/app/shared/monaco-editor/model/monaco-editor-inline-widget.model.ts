import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import { MonacoEditorViewZone } from 'app/shared/monaco-editor/model/monaco-editor-view-zone.model';
import { MonacoEditorOverlayWidget } from 'app/shared/monaco-editor/model/monaco-editor-overlay-widget.model';

import * as monaco from 'monaco-editor';

export class MonacoEditorInlineWidget extends MonacoCodeEditorElement {
    private viewZone: MonacoEditorViewZone;
    private overlayWidget: MonacoEditorOverlayWidget;

    constructor(editor: monaco.editor.ICodeEditor, overlayWidgetId: string, contentDomNode: HTMLElement, afterLineNumber: number) {
        super(editor, overlayWidgetId);
        this.overlayWidget = new MonacoEditorOverlayWidget(
            editor,
            overlayWidgetId,
            contentDomNode,
            null, // Position is managed by viewZone.
        );
        this.viewZone = new MonacoEditorViewZone(editor, afterLineNumber, contentDomNode);
    }

    addToEditor() {
        this.overlayWidget.addToEditor();
        this.viewZone.addToEditor();
    }

    removeFromEditor() {
        this.overlayWidget.removeFromEditor();
        this.viewZone.removeFromEditor();
    }

    dispose() {
        super.dispose();
        this.overlayWidget.dispose();
        this.viewZone.dispose();
    }
}
