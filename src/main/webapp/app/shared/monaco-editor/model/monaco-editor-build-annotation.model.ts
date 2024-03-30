import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import { MonacoEditorGlyphMarginWidget } from 'app/shared/monaco-editor/model/monaco-editor-glyph-margin-widget.model';

import * as monaco from 'monaco-editor';

export enum MonacoEditorAnnotationTypeEnum {
    WARNING = 'warning',
    ERROR = 'error',
}

export class MonacoEditorBuildAnnotation extends MonacoCodeEditorElement {
    private glyphMarginWidget: MonacoEditorGlyphMarginWidget;
    private decorationsCollection: monaco.editor.IEditorDecorationsCollection;
    private outdated: boolean;
    private hoverMessage: string;
    private type: MonacoEditorAnnotationTypeEnum;

    constructor(editor: monaco.editor.ICodeEditor, id: string, lineNumber: number, hoverMessage: string, type: MonacoEditorAnnotationTypeEnum, outdated = false) {
        super(editor, id);
        this.decorationsCollection = this.editor.createDecorationsCollection([]);
        this.hoverMessage = hoverMessage;
        this.type = type;
        this.outdated = outdated;
        const marginGlyphDomNode = document.createElement('div');
        marginGlyphDomNode.className = `codicon codicon-${this.type}`;
        this.glyphMarginWidget = new MonacoEditorGlyphMarginWidget(editor, id, marginGlyphDomNode, lineNumber);
    }

    private getAssociatedDeltaDecoration(): monaco.editor.IModelDeltaDecoration {
        const lineNumber = this.getLineNumber();
        return {
            range: new monaco.Range(lineNumber, 0, lineNumber, 0),
            options: {
                marginClassName: 'monaco-annotation-error',
                isWholeLine: true,
                stickiness: monaco.editor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges,
                lineNumberHoverMessage: { value: this.hoverMessage },
                glyphMarginHoverMessage: { value: this.hoverMessage },
            },
        };
    }

    getLineNumber(): number {
        return this.glyphMarginWidget.getLineNumber();
    }

    addToEditor(): void {
        this.setVisible(true);
        this.glyphMarginWidget.addToEditor();
        this.decorationsCollection.append([this.getAssociatedDeltaDecoration()]);
    }
    removeFromEditor(): void {
        this.glyphMarginWidget.removeFromEditor();
        this.decorationsCollection.clear();
    }
}
