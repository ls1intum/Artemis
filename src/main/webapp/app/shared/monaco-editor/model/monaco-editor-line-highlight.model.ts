import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import * as monaco from 'monaco-editor';

export class MonacoEditorLineHighlight extends MonacoCodeEditorElement {
    private range: monaco.IRange;
    private className?: string;
    private marginClassName?: string;
    private decorationsCollection: monaco.editor.IEditorDecorationsCollection;

    constructor(editor: monaco.editor.ICodeEditor, id: string, startLine: number, endLine?: number, className?: string, marginClassName?: string) {
        super(editor, id);
        this.range = new monaco.Range(startLine, 0, endLine ?? startLine, 0);
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
