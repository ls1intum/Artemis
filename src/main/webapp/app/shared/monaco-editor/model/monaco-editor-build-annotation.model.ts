import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import { MonacoEditorGlyphMarginWidget } from 'app/shared/monaco-editor/model/monaco-editor-glyph-margin-widget.model';

import * as monaco from 'monaco-editor';

export enum MonacoEditorBuildAnnotationType {
    WARNING = 'warning',
    ERROR = 'error',
}

export class MonacoEditorBuildAnnotation extends MonacoCodeEditorElement {
    private glyphMarginWidget: MonacoEditorGlyphMarginWidget;
    private decorationsCollection: monaco.editor.IEditorDecorationsCollection;
    private outdated: boolean;
    private hoverMessage: string;
    private type: MonacoEditorBuildAnnotationType;
    private updateListener: monaco.IDisposable;

    constructor(editor: monaco.editor.ICodeEditor, id: string, lineNumber: number, hoverMessage: string, type: MonacoEditorBuildAnnotationType, outdated = false) {
        super(editor, id);
        this.decorationsCollection = this.editor.createDecorationsCollection([]);
        this.hoverMessage = hoverMessage;
        this.type = type;
        this.outdated = outdated;
        const marginGlyphDomNode = document.createElement('div');
        marginGlyphDomNode.className = `codicon codicon-${this.type}`;
        this.glyphMarginWidget = new MonacoEditorGlyphMarginWidget(editor, id, marginGlyphDomNode, lineNumber);
        this.setupListeners();
    }

    private getAssociatedDeltaDecoration(): monaco.editor.IModelDeltaDecoration {
        const marginClassName = this.outdated ? 'monaco-annotation-outdated' : `monaco-annotation-${this.type}`;
        const lineNumber = this.getLineNumber();
        return {
            range: new monaco.Range(lineNumber, 0, lineNumber, 0),
            options: {
                marginClassName,
                isWholeLine: true,
                stickiness: monaco.editor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges,
                lineNumberHoverMessage: { value: this.hoverMessage },
                glyphMarginHoverMessage: { value: this.hoverMessage },
            },
        };
    }

    setOutdatedAndUpdate(outdated: boolean) {
        this.outdated = outdated;
        const classList = this.glyphMarginWidget.getDomNode().classList;
        if (outdated) {
            classList.remove(`monaco-glyph-${this.type}`);
            classList.add(`monaco-glyph-outdated`);
        } else {
            classList.remove(`monaco-glyph-outdated`);
            classList.add(`monaco-glyph-${this.type}`);
        }
        this.updateInEditor();
    }

    isOutdated(): boolean {
        return this.outdated;
    }

    getLineNumber(): number {
        return this.glyphMarginWidget.getLineNumber();
    }

    getGlyphMarginDomNode(): HTMLElement {
        return this.glyphMarginWidget.getDomNode();
    }

    protected setupListeners() {
        this.updateListener = this.editor.onDidChangeModelContent(() => {
            this.setOutdatedAndUpdate(true);
        });
    }

    addToEditor(): void {
        const inRange = this.getLineNumber() <= (this.editor.getModel()?.getLineCount() ?? 0);
        this.setVisible(inRange);
        if (inRange) {
            this.glyphMarginWidget.addToEditor();
            this.decorationsCollection.append([this.getAssociatedDeltaDecoration()]);
        }
    }
    removeFromEditor(): void {
        this.glyphMarginWidget.removeFromEditor();
        this.decorationsCollection.clear();
    }

    dispose() {
        super.dispose();
        this.glyphMarginWidget.dispose();
        this.updateListener.dispose();
    }
}
