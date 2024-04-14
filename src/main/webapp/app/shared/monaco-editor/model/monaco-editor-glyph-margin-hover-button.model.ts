import * as monaco from 'monaco-editor';
import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import { MonacoEditorGlyphMarginWidget } from 'app/shared/monaco-editor/model/monaco-editor-glyph-margin-widget.model';
export class MonacoEditorGlyphMarginHoverButton extends MonacoCodeEditorElement {
    readonly glyphMarginWidget: MonacoEditorGlyphMarginWidget;
    private mouseMoveListener?: monaco.IDisposable;
    private cursorPositionListener?: monaco.IDisposable;
    private readonly clickEventCallback: () => void;

    constructor(editor: monaco.editor.ICodeEditor, id: string, domNode: HTMLElement, clickCallback: (lineNumber: number) => void) {
        super(editor, id);
        this.clickEventCallback = () => {
            clickCallback(this.getCurrentLineNumber());
        };
        this.glyphMarginWidget = new MonacoEditorGlyphMarginWidget(editor, id, domNode, 1);
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
            const lineNumber = editorMouseEvent.target?.range?.startLineNumber;
            this.moveAndUpdate(lineNumber);
        });

        /**
         * This is mainly required for the E2E tests.
         */
        this.cursorPositionListener = this.editor.onDidChangeCursorPosition((e: monaco.editor.ICursorPositionChangedEvent) => {
            this.moveAndUpdate(e.position.lineNumber);
        });
    }

    moveAndUpdate(lineNumber?: number) {
        if (!!lineNumber && lineNumber !== this.getCurrentLineNumber()) {
            this.glyphMarginWidget.setLineNumber(lineNumber);
            this.updateInEditor();
        }
    }

    addToEditor(): void {
        this.glyphMarginWidget.addToEditor();
    }

    removeFromEditor(): void {
        this.glyphMarginWidget.removeFromEditor();
    }

    dispose(): void {
        super.dispose();
        this.mouseMoveListener?.dispose();
        this.cursorPositionListener?.dispose();
        this.getDomNode().removeEventListener('click', this.clickEventCallback);
        this.glyphMarginWidget.dispose();
    }
}
