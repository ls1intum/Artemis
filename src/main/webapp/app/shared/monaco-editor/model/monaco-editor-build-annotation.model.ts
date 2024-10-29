import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import { MonacoEditorGlyphMarginWidget } from 'app/shared/monaco-editor/model/monaco-editor-glyph-margin-widget.model';
import { Disposable, makeEditorRange } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import * as monaco from 'monaco-editor';

export enum MonacoEditorBuildAnnotationType {
    WARNING = 'warning',
    ERROR = 'error',
}

/**
 * Class representing a build annotation (error / warning with description) rendered on the margin of the Monaco editor.
 * They remain fixed to their line even when the user makes edits.
 * Annotations consist of a {@link MonacoEditorGlyphMarginWidget} to render an icon in the glyph margin and a separate
 * decoration (managed by the {@link decorationsCollection}) to handle highlighting and the hover message.
 */
export class MonacoEditorBuildAnnotation extends MonacoCodeEditorElement {
    private glyphMarginWidget: MonacoEditorGlyphMarginWidget;
    private decorationsCollection: monaco.editor.IEditorDecorationsCollection;
    private outdated: boolean;
    private hoverMessage: string;
    private type: MonacoEditorBuildAnnotationType;
    private updateListener: Disposable;

    /**
     * @param editor The editor to render this annotation in.
     * @param id The id of this annotation.
     * @param lineNumber The line this annotation refers to.
     * @param hoverMessage The message to display when the user hovers over this annotation. Can have markdown elements, e.g. `**bold**`.
     * @param type The type of this annotation: error or warning.
     * @param outdated Whether this annotation is outdated and should be grayed out. Defaults to false.
     */
    constructor(editor: monaco.editor.IStandaloneCodeEditor, id: string, lineNumber: number, hoverMessage: string, type: MonacoEditorBuildAnnotationType, outdated = false) {
        super(editor, id);
        this.decorationsCollection = this.editor.createDecorationsCollection([]);
        this.hoverMessage = hoverMessage;
        this.type = type;
        this.outdated = outdated;
        const glyphMarginDomNode = document.createElement('div');
        glyphMarginDomNode.id = `monaco-editor-glyph-margin-widget-${id}`;
        glyphMarginDomNode.className = `codicon codicon-${this.type}`;
        this.glyphMarginWidget = new MonacoEditorGlyphMarginWidget(editor, id, glyphMarginDomNode, lineNumber, monaco.editor.GlyphMarginLane.Center);
        this.setupListeners();
    }

    /**
     * Returns an object (a delta decoration) detailing the position and styling of the annotation.
     * @private
     */
    private getAssociatedDeltaDecoration(): monaco.editor.IModelDeltaDecoration {
        const marginClassName = this.outdated ? 'monaco-annotation-outdated' : `monaco-annotation-${this.type}`;
        const lineNumber = this.getLineNumber();
        return {
            range: makeEditorRange(lineNumber, 0, lineNumber, 0),
            options: {
                marginClassName,
                isWholeLine: true,
                stickiness: monaco.editor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges,
                lineNumberHoverMessage: { value: this.hoverMessage },
                glyphMarginHoverMessage: { value: this.hoverMessage },
            },
        };
    }

    /**
     * Updates the style of this annotation and its linked glyph margin widget according to whether the annotation is outdated.
     * @param outdated Whether this annotation is outdated and should be grayed out.
     */
    setOutdatedAndUpdate(outdated: boolean) {
        this.outdated = outdated;
        const classList = this.getGlyphMarginDomNode().classList;
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
            // The displayed annotations may not apply anymore if the files have changed. For convenience, we still display them for the user's reference.
            this.setOutdatedAndUpdate(true);
        });
    }

    addToEditor(): void {
        const inRange = this.getLineNumber() <= (this.editor.getModel()?.getLineCount() ?? 0);
        this.setVisible(inRange);
        if (inRange) {
            this.glyphMarginWidget.addToEditor();
            // Appending to this collection immediately renders the associated decoration.
            this.decorationsCollection.append([this.getAssociatedDeltaDecoration()]);
        }
    }
    removeFromEditor(): void {
        this.glyphMarginWidget.removeFromEditor();
        // Clearing the collection immediately removes all decorations from the editor.
        this.decorationsCollection.clear();
    }

    dispose() {
        super.dispose();
        this.glyphMarginWidget.dispose();
        this.updateListener.dispose();
    }
}
