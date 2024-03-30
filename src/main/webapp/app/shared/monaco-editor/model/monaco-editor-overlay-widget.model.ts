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
        this.position = position;
    }

    setVisible(visible: boolean) {
        super.setVisible(visible);
        this.domNode.style.width = '100%';
        this.domNode.style.display = visible ? 'unset' : 'none';
    }

    addToEditor(): void {
        this.editor.addOverlayWidget(this);
        this.setVisible(true);
    }
    removeFromEditor(): void {
        this.editor.removeOverlayWidget(this);
    }

    /*
     * From IOverlayWidget
     */
    getDomNode(): HTMLElement {
        return this.domNode;
    }
    getPosition(): OverlayWidgetPosition {
        return this.position;
    }
}
