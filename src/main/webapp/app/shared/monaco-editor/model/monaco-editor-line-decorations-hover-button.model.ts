import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import * as monaco from 'monaco-editor';

export class MonacoEditorLineDecorationsHoverButton extends MonacoCodeEditorElement {
    private clickCallback: (lineNumber: number) => void;
    private currentLineNumber: number = 1;
    private decorationsCollection: monaco.editor.IEditorDecorationsCollection;
    private readonly className: string;

    private mouseMoveListener?: monaco.IDisposable;
    private mouseDownListener?: monaco.IDisposable;
    private mouseLeaveListener?: monaco.IDisposable;

    constructor(editor: monaco.editor.ICodeEditor, id: string, className: string, clickCallback: (lineNumber: number) => void) {
        super(editor, id);
        this.clickCallback = clickCallback;
        this.className = className;
        this.decorationsCollection = editor.createDecorationsCollection([]);
        // The button is only visible once the mouse triggers its appearance.
        this.setVisible(false);
        this.setupListeners();
    }

    protected setupListeners() {
        super.setupListeners();
        this.mouseMoveListener = this.editor.onMouseMove((editorMouseEvent: monaco.editor.IEditorMouseEvent) => {
            // This is undefined e.g. when hovering over a line widget.
            const lineNumber = editorMouseEvent.target?.position?.lineNumber;
            this.moveAndUpdate(lineNumber);
        });

        this.mouseLeaveListener = this.editor.onMouseLeave(() => {
            this.removeFromEditor();
        });

        this.mouseDownListener = this.editor.onMouseDown((editorMouseEvent: monaco.editor.IEditorMouseEvent) => {
            const lineNumber = editorMouseEvent.target?.position?.lineNumber;
            // We identify the button via the class name of the element.
            if (lineNumber && editorMouseEvent.target?.element?.classList?.contains(this.className)) {
                this.clickCallback(lineNumber);
            }
        });
    }

    moveAndUpdate(lineNumber?: number): void {
        if (lineNumber === undefined) {
            this.removeFromEditor();
        } else if (!this.isVisible() || lineNumber !== this.currentLineNumber) {
            this.currentLineNumber = lineNumber;
            this.updateInEditor();
        }
    }

    dispose() {
        super.dispose();
        this.mouseMoveListener?.dispose();
        this.mouseDownListener?.dispose();
        this.mouseLeaveListener?.dispose();
    }

    addToEditor(): void {
        this.setVisible(true);
        this.decorationsCollection.set([this.getAssociatedDeltaDecoration()]);
    }

    private getAssociatedDeltaDecoration(): monaco.editor.IModelDeltaDecoration {
        return {
            range: new monaco.Range(this.currentLineNumber, 1, this.currentLineNumber, 1),
            options: {
                isWholeLine: true,
                linesDecorationsClassName: this.className,
            },
        };
    }

    removeFromEditor(): void {
        this.setVisible(false);
        this.decorationsCollection.clear();
    }
}
