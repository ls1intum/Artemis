import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import * as monaco from 'monaco-editor';

// null is used by the monaco editor API
type OverlayWidgetPosition = monaco.editor.IOverlayWidgetPosition | null;
export class MonacoEditorOverlayWidget extends MonacoCodeEditorElement implements monaco.editor.IOverlayWidget {
    private readonly domNode: HTMLElement;
    private readonly position: OverlayWidgetPosition;

    /**
     * Constructs an overlay widget.
     * @param editor The editor to render the widget in.
     * @param id A unique identifier for the widget.
     * @param domNode The content to render. The user will be able to interact with the widget.
     * @param position The position of the widget or null if the element is positioned by another element (e.g. a view zone).
     */
    constructor(editor: monaco.editor.ICodeEditor, id: string, domNode: HTMLElement, position: OverlayWidgetPosition) {
        super(editor, id);
        this.domNode = domNode;
        // At the moment, the inline feedback nodes will only reach their maximum width with the following line. This workaround can be removed as soon as the Ace editor has been replaced.
        this.domNode.style.width = '100%';
        this.position = position;
    }

    setVisible(visible: boolean) {
        super.setVisible(visible);
        this.setHtmlElementsVisible(visible, this.domNode);
    }

    addToEditor(): void {
        this.editor.addOverlayWidget(this);
        this.setVisible(true);
    }
    removeFromEditor(): void {
        this.editor.removeOverlayWidget(this);
    }

    getDomNode(): HTMLElement {
        return this.domNode;
    }
    getPosition(): OverlayWidgetPosition {
        return this.position;
    }
}
