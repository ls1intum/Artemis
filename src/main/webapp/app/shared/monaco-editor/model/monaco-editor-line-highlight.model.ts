import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import { makeEditorRange } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import * as monaco from 'monaco-editor';

/**
 * Class representing a highlighted range of lines in the Monaco editor.
 * The highlighted lines can have separate styles for the margin and the line.
 */
export class MonacoEditorLineHighlight extends MonacoCodeEditorElement {
    private range: monaco.IRange;
    private className?: string;
    private marginClassName?: string;
    private decorationsCollection: monaco.editor.IEditorDecorationsCollection;

    /**
     * @param editor The editor to highlight lines in.
     * @param id The ID of this element.
     * @param startLine The number of the first line to highlight.
     * @param endLine The number of the last line to highlight.
     * @param className The class name to use for highlighting the line. If left out, no class will be applied.
     * @param marginClassName The class name to use for highlighting the margin. If left out, no class will be applied.
     */
    constructor(editor: monaco.editor.IStandaloneCodeEditor, id: string, startLine: number, endLine: number, className?: string, marginClassName?: string) {
        super(editor, id);
        this.range = makeEditorRange(startLine, 0, endLine, 0);
        this.className = className;
        this.marginClassName = marginClassName;
        this.decorationsCollection = editor.createDecorationsCollection([]);
    }

    private getAssociatedDeltaDecoration(): monaco.editor.IModelDeltaDecoration {
        return {
            range: this.range,
            options: {
                marginClassName: this.marginClassName,
                className: this.className,
                isWholeLine: true,
                stickiness: monaco.editor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges,
            },
        };
    }

    addToEditor(): void {
        this.decorationsCollection.append([this.getAssociatedDeltaDecoration()]);
    }

    removeFromEditor(): void {
        this.decorationsCollection.clear();
    }
}
