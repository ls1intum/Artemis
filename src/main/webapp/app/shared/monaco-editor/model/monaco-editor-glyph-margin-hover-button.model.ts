import * as monaco from 'monaco-editor';
import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import { MonacoEditorGlyphMarginWidget } from 'app/shared/monaco-editor/model/monaco-editor-glyph-margin-widget.model';

/**
 * Class representing a button on the glyph margin of the Monaco editor.
 * It becomes visible when the user hovers over the corresponding line and uses a {@link MonacoEditorGlyphMarginWidget} for the button.
 */
export class MonacoEditorGlyphMarginHoverButton extends MonacoCodeEditorElement {
    readonly glyphMarginWidget: MonacoEditorGlyphMarginWidget;
    private mouseMoveListener?: monaco.IDisposable;
    private mouseLeaveListener?: monaco.IDisposable;
    private readonly clickEventCallback: () => void;

    /**
     * @param editor The editor to render this button in.
     * @param id The id of this annotation.
     * @param domNode The DOM node to display as the button. An event listener will be attached to this node.
     * @param clickCallback The callback to execute when the button is clicked. It receives the line number (as written in the editor)
     * of the line where the button was positioned during the click.
     */
    constructor(editor: monaco.editor.ICodeEditor, id: string, domNode: HTMLElement, clickCallback: (lineNumber: number) => void) {
        super(editor, id);
        this.clickEventCallback = () => {
            clickCallback(this.getCurrentLineNumber());
        };
        // The button is only visible once the mouse triggers its appearance.
        this.setVisible(false);
        this.glyphMarginWidget = new MonacoEditorGlyphMarginWidget(editor, id, domNode, 1, monaco.editor.GlyphMarginLane.Left);
        this.setupListeners();
    }

    getCurrentLineNumber(): number {
        return this.glyphMarginWidget.getLineNumber();
    }

    getDomNode(): HTMLElement {
        return this.glyphMarginWidget.getDomNode();
    }

    protected setupListeners(): void {
        this.getDomNode().addEventListener('click', this.clickEventCallback);
        this.mouseMoveListener = this.editor.onMouseMove((editorMouseEvent: monaco.editor.IEditorMouseEvent) => {
            // This is undefined e.g. when hovering over a line widget.
            const lineNumber = editorMouseEvent.target?.range?.startLineNumber;
            this.moveAndUpdate(lineNumber);
        });

        this.mouseLeaveListener = this.editor.onMouseLeave(() => {
            this.removeFromEditor();
        });
    }

    /**
     * Moves and re-renders the button if necessary.
     * @param lineNumber The new line to place the button in. If the button is already shown in this line, nothing will happen.
     * If the number is undefined, the widget will be removed.
     */
    moveAndUpdate(lineNumber?: number): void {
        if (lineNumber === undefined) {
            this.removeFromEditor();
        } else if (!this.isVisible() || lineNumber !== this.getCurrentLineNumber()) {
            this.glyphMarginWidget.setLineNumber(lineNumber);
            this.updateInEditor();
        }
    }

    addToEditor(): void {
        this.setVisible(true);
        this.glyphMarginWidget.addToEditor();
    }

    removeFromEditor(): void {
        this.setVisible(false);
        this.glyphMarginWidget.removeFromEditor();
    }

    dispose(): void {
        super.dispose();
        this.mouseMoveListener?.dispose();
        this.mouseLeaveListener?.dispose();
        this.getDomNode().removeEventListener('click', this.clickEventCallback);
        this.glyphMarginWidget.dispose();
    }
}
