import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import * as monaco from 'monaco-editor';
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
