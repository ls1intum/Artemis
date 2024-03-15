import * as monaco from 'monaco-editor';
import { MarkdownString } from 'app/shared/monaco-editor/monaco-editor.component';

export enum MonacoEditorAnnotationType {
    WARNING = 'warning',
    ERROR = 'error',
}
export type MonacoEditorAnnotationPosition = monaco.editor.IGlyphMarginWidgetPosition;

export class MonacoEditorAnnotation implements monaco.editor.IGlyphMarginWidget, monaco.IDisposable {
    private readonly id: string;
    private readonly position: MonacoEditorAnnotationPosition;
    private readonly domNode: HTMLElement;
    private readonly hoverMessage: MarkdownString;
    private readonly type: MonacoEditorAnnotationType;
    private outdated = false;

    private decorationsCollection: monaco.editor.IEditorDecorationsCollection;
    private updateListener: monaco.IDisposable;

    /**
     * Create a new editor annotation that highlights an error or a warning.
     * @param id A unique identifier for this annotation.
     * @param lineNumber The line on which this widget should be rendered.
     * @param domNode The DOM node to display in the glyph margin. If undefined, a new div node will be created
     * with the classes `codicon` and `codicon-error` or `codicon-warning` depending on the specified type.
     * @param hoverMessage The message to display when hovering over the glyph or the margin of the editor.
     * @param type The type of the annotation. Determines the highlighting.
     * @param emptyDecorationsCollection An empty decorations collection, created by createDecorationsCollection.
     */
    constructor(
        id: string,
        lineNumber: number,
        domNode: HTMLElement | undefined,
        hoverMessage: MarkdownString,
        type: MonacoEditorAnnotationType,
        emptyDecorationsCollection: monaco.editor.IEditorDecorationsCollection,
    ) {
        this.id = id;
        this.position = {
            lane: monaco.editor.GlyphMarginLane.Center,
            zIndex: 10,
            range: new monaco.Range(lineNumber, 0, lineNumber, 0),
        };
        if (domNode) {
            this.domNode = domNode;
        } else {
            this.domNode = document.createElement('div');
            this.domNode.className = `codicon codicon-${type}`;
        }
        this.domNode.id = `monaco-editor-annotation-${id}`;
        this.hoverMessage = hoverMessage;
        this.type = type;
        this.decorationsCollection = emptyDecorationsCollection;
        this.decorationsCollection.append([this.getAssociatedDeltaDecoration()]);
    }

    dispose(): void {
        this.decorationsCollection.clear();
        this.updateListener?.dispose();
    }

    getId(): string {
        return this.id;
    }

    getPosition(): MonacoEditorAnnotationPosition {
        return this.position;
    }

    getDomNode(): HTMLElement {
        return this.domNode;
    }

    setOutdated(outdated: boolean) {
        this.outdated = outdated;
        this.domNode.style.color = outdated ? 'darkgray' : 'unset';
        this.domNode.style.opacity = outdated ? '40%' : '100%';
    }

    /**
     * This method removes and subsequently recreates the decoration associated with this widget,
     * thus forcing it to remain at its intended position.
     * If the line no longer exists in the editor, then the entire element is hidden.
     */
    public updateDecoration(numberOfLines: number) {
        this.setOutdated(true);
        if (this.getLineNumber() <= numberOfLines) {
            this.domNode.style.visibility = 'visible';
            this.decorationsCollection.clear();
            this.decorationsCollection.append([this.getAssociatedDeltaDecoration()]);
        } else {
            this.domNode.style.visibility = 'hidden';
            this.decorationsCollection.clear();
        }
    }

    public setUpdateListener(updateListener: monaco.IDisposable) {
        this.updateListener = updateListener;
    }

    getLineNumber() {
        return this.getPosition().range.startLineNumber;
    }

    getAssociatedDeltaDecoration(): monaco.editor.IModelDeltaDecoration {
        return {
            range: this.position.range,
            options: {
                marginClassName: this.getDecorationMarginClassName(),
                isWholeLine: true,
                stickiness: monaco.editor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges,
                lineNumberHoverMessage: this.hoverMessage,
                glyphMarginHoverMessage: this.hoverMessage,
            },
        };
    }

    private getDecorationMarginClassName(): string {
        const suffix = this.outdated ? 'outdated' : this.type.toString();
        return `monaco-annotation-${suffix}`;
    }
}
