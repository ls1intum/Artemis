import * as monaco from 'monaco-editor';

export abstract class MonacoCodeEditorElement implements monaco.IDisposable {
    private id: string | undefined;
    private visible = true;
    protected readonly editor: monaco.editor.ICodeEditor;

    protected constructor(editor: monaco.editor.ICodeEditor, id: string | undefined) {
        this.editor = editor;
        this.id = id;
    }

    getId(): string {
        if (this.id === undefined) {
            throw new Error('This editor element has no ID.');
        }
        return this.id;
    }

    setId(id: string): void {
        this.id = id;
    }

    setVisible(visible: boolean): void {
        this.visible = visible;
    }

    isVisible(): boolean {
        return this.visible;
    }

    protected setupListeners(): void {}
    abstract addToEditor(): void;
    updateInEditor(): void {
        this.removeFromEditor();
        this.addToEditor();
    }
    abstract removeFromEditor(): void;
    /**
     * Removes this element from the editor. Override this to dispose of any additional listeners, subscribers etc.
     */
    dispose(): void {
        this.removeFromEditor();
    }
}
