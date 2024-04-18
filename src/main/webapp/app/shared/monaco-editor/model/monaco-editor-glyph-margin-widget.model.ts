import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import * as monaco from 'monaco-editor';

/**
 * Class representing a glyph margin widget in the Monaco editor.
 * Glyph margin widgets refer to one line and can contain arbitrary DOM nodes.
 */
export class MonacoEditorGlyphMarginWidget extends MonacoCodeEditorElement implements monaco.editor.IGlyphMarginWidget {
    private readonly domNode: HTMLElement;
    private readonly lineNumber: number;

    constructor(editor: monaco.editor.ICodeEditor, id: string, domNode: HTMLElement, lineNumber: number) {
        super(editor, id);
        this.domNode = domNode;
        this.lineNumber = lineNumber;
    }

    getDomNode(): HTMLElement {
        return this.domNode;
    }
    getPosition(): monaco.editor.IGlyphMarginWidgetPosition {
        return {
            // The Center lane allows for rendering of hover messages above this widget.
            lane: monaco.editor.GlyphMarginLane.Center,
            zIndex: 10,
            range: new monaco.Range(this.lineNumber, 0, this.lineNumber, 0),
        };
    }

    getLineNumber(): number {
        return this.lineNumber;
    }

    addToEditor(): void {
        this.editor.addGlyphMarginWidget(this);
    }
    removeFromEditor(): void {
        this.editor.removeGlyphMarginWidget(this);
    }
}
