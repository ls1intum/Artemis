import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import { Disposable, makeEditorRange } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import * as monaco from 'monaco-editor';

/**
 * Class representing a hover button that is displayed on a specific line in the editor.
 * The button is displayed in the line decorations of the editor; i.e., it is between the line numbers and the code.
 */
export class MonacoEditorLineDecorationsHoverButton extends MonacoCodeEditorElement {
    private clickCallback: (lineNumber: number) => void;
    private currentLineNumber = 1;
    private decorationsCollection: monaco.editor.IEditorDecorationsCollection;
    private readonly className: string;

    private mouseMoveListener?: Disposable;
    private mouseDownListener?: Disposable;
    private mouseLeaveListener?: Disposable;

    /**
     * @param editor The editor to which to add the button.
     * @param id The unique id of the button.
     * @param className The class name of the button. This is used to uniquely identify the button in the editor.
     * @param clickCallback The callback to be called when the button is clicked. The line number of the button is passed as an argument.
     */
    constructor(editor: monaco.editor.IStandaloneCodeEditor, id: string, className: string, clickCallback: (lineNumber: number) => void) {
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

        this.mouseDownListener = this.editor.onMouseDown(this.onClick.bind(this));
    }

    /**
     * Checks if the button was clicked and calls the click callback with the line number as an argument.
     * @param editorMouseEvent The mouse event to react to.
     */
    onClick(editorMouseEvent: monaco.editor.IEditorMouseEvent): void {
        const lineNumber = editorMouseEvent.target?.position?.lineNumber;
        // We identify the button via the class name of the element.
        if (lineNumber && editorMouseEvent.target?.element?.classList?.contains(this.className)) {
            this.clickCallback(lineNumber);
        }
    }

    /**
     * Move the button to the specified line number and update the editor. If the line number is undefined, the button is instead removed from the editor.
     * @param lineNumber The line number to which to move the button, if any.
     */
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
            range: makeEditorRange(this.currentLineNumber, 1, this.currentLineNumber, 1),
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
