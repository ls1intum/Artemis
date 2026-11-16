import { MonacoCodeEditorElement } from 'app/editor/monaco-editor/model/monaco-code-editor-element.model';
import { MonacoEditorViewZone } from 'app/editor/monaco-editor/model/monaco-editor-view-zone.model';
import { MonacoEditorOverlayWidget } from 'app/editor/monaco-editor/model/monaco-editor-overlay-widget.model';
import * as monaco from 'monaco-editor';

/**
 * Class representing a line widget.
 * These widgets consist of two elements:
 *   - a {@link MonacoEditorViewZone} that creates vertical space after the line in question.
 *   - a {@link MonacoEditorOverlayWidget} that contains the actual content of the widget.
 * The size of these two components is linked together.
 */
export class MonacoEditorLineWidget extends MonacoCodeEditorElement {
    private viewZone: MonacoEditorViewZone;
    private overlayWidget: MonacoEditorOverlayWidget;
    private layoutListener?: monaco.IDisposable;

    /**
     * @param editor The editor to render this widget in.
     * @param overlayWidgetId The ID to use for the overlay widget.
     * @param contentDomNode The content to render.
     * @param afterLineNumber The line after which this line widget should be rendered.
     * @param alignWithCode Whether the widget starts where the code does, so it leaves the line numbers and the gutter uncovered, instead of spanning the editor.
     */
    constructor(editor: monaco.editor.IStandaloneCodeEditor, overlayWidgetId: string, contentDomNode: HTMLElement, afterLineNumber: number, alignWithCode = false) {
        super(editor, overlayWidgetId);
        this.overlayWidget = new MonacoEditorOverlayWidget(
            editor,
            overlayWidgetId,
            contentDomNode,
            null, // Position is managed by viewZone.
        );
        this.viewZone = new MonacoEditorViewZone(editor, afterLineNumber, contentDomNode);
        if (alignWithCode) {
            const alignWithContent = () => {
                const contentLeft = editor.getLayoutInfo().contentLeft;
                contentDomNode.style.marginLeft = `${contentLeft}px`;
                contentDomNode.style.width = `calc(100% - ${contentLeft}px)`;
            };
            alignWithContent();
            // The gutter changes its width with the number of lines and with the hover button of the line decorations
            this.layoutListener = editor.onDidLayoutChange(alignWithContent);
        }
    }

    addToEditor() {
        this.overlayWidget.addToEditor();
        this.viewZone.addToEditor();
    }

    removeFromEditor() {
        this.overlayWidget.removeFromEditor();
        this.viewZone.removeFromEditor();
    }

    override dispose() {
        super.dispose();
        this.layoutListener?.dispose();
        this.overlayWidget.dispose();
        this.viewZone.dispose();
    }
}
