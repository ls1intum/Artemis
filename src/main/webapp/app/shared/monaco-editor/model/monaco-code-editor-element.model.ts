import { Disposable } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import * as monaco from 'monaco-editor';

/**
 * Abstract class representing an element in the Monaco editor, e.g. a line widget.
 */
export abstract class MonacoCodeEditorElement implements Disposable {
    static readonly CSS_HIDDEN_CLASS = 'monaco-hidden-element';

    private id: string | undefined;
    private visible = true;
    protected readonly editor: monaco.editor.IStandaloneCodeEditor;

    /**
     * @param editor The editor to render this element in.
     * @param id The id of this element if available, or undefined if not. If the ID is not available at construction time, it must be set using {@link setId}.
     */
    protected constructor(editor: monaco.editor.IStandaloneCodeEditor, id: string | undefined) {
        this.editor = editor;
        this.id = id;
    }

    /**
     * Returns the id of this element. Since Monaco expects the element to always be available, this method throws an error if it has not been set yet.
     */
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

    /**
     * Override this method to set up listeners that react to changes in the editor.
     */
    protected setupListeners(): void {}

    /**
     * Adds this element to the editor.
     */
    abstract addToEditor(): void;

    /**
     * Updates this element in the editor, e.g. by removing and adding it again.
     * Override this method to specify custom behavior for updating elements.
     */
    updateInEditor(): void {
        this.removeFromEditor();
        this.addToEditor();
    }

    /**
     * Removes this element from the editor, but does not destroy it.
     * The element can be added to the editor again using {@link addToEditor}.
     */
    abstract removeFromEditor(): void;

    /**
     * Removes this element from the editor. Override this to dispose of any additional listeners, subscribers etc.
     */
    dispose(): void {
        this.removeFromEditor();
    }

    /**
     * Updates html elements to be visible  using a CSS class. This assumes that the visibility is controlled only by the CSS class of the provided elements.
     * @param visible Whether the elements should be made visible.
     * @param htmlElements The elements whose visibility should be updated using the {@link MonacoCodeEditorElement.CSS_HIDDEN_CLASS} CSS class.
     */
    setHtmlElementsVisible(visible: boolean, ...htmlElements: HTMLElement[]): void {
        if (visible) {
            htmlElements.forEach((element) => element.classList.remove(MonacoCodeEditorElement.CSS_HIDDEN_CLASS));
        } else {
            htmlElements.forEach((element) => element.classList.add(MonacoCodeEditorElement.CSS_HIDDEN_CLASS));
        }
    }
}
