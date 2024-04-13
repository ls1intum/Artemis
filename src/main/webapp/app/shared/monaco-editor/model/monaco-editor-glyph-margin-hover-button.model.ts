import * as monaco from 'monaco-editor';
import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import { MonacoEditorGlyphMarginWidget } from 'app/shared/monaco-editor/model/monaco-editor-glyph-margin-widget.model';

import MouseTargetType = monaco.editor.MouseTargetType;
export class MonacoEditorGlyphMarginHoverButton extends MonacoCodeEditorElement {
    readonly glyphMarginWidget: MonacoEditorGlyphMarginWidget;
    private mouseMoveListener?: monaco.IDisposable;
    private mouseLeaveListener?: monaco.IDisposable;
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
            const hoveringOverGutter = [MouseTargetType.GUTTER_LINE_NUMBERS, MouseTargetType.GUTTER_GLYPH_MARGIN].includes(editorMouseEvent.target.type);
            const lineNumber = editorMouseEvent.target?.range?.startLineNumber;
            if (hoveringOverGutter && !!lineNumber && lineNumber !== this.getCurrentLineNumber()) {
                this.glyphMarginWidget.setLineNumber(lineNumber);
                this.updateInEditor();
            } else if (!hoveringOverGutter) {
                this.removeFromEditor();
            }
        });
        this.mouseLeaveListener = this.editor.onMouseLeave(() => {
            this.removeFromEditor();
        });
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
        this.mouseLeaveListener?.dispose();
        this.getDomNode().removeEventListener('click', this.clickEventCallback);
        this.glyphMarginWidget.dispose();
    }
}
