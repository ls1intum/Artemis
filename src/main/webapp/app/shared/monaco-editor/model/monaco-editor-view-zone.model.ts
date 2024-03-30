import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import * as monaco from 'monaco-editor';

export class MonacoEditorViewZone extends MonacoCodeEditorElement implements monaco.editor.IViewZone {
    private linkedContentDomNode: HTMLElement;
    private resizeObserver: ResizeObserver;

    /*
     * From IViewZone
     */
    afterLineNumber: number;
    heightInPx: number | undefined;

    /**
     * From IViewZone. The domNode of a view zone cannot be interacted with. Therefore, we instantiate it as an empty div.
     */
    domNode: HTMLElement = document.createElement('div');

    constructor(editor: monaco.editor.ICodeEditor, afterLineNumber: number, linkedContentDomNode: HTMLElement) {
        // id is unavailable until the view zone is added to the editor.
        super(editor, undefined);
        this.afterLineNumber = afterLineNumber;
        this.linkedContentDomNode = linkedContentDomNode;
        this.heightInPx = this.linkedContentDomNode.offsetHeight;
    }

    addToEditor(): void {
        this.editor.changeViewZones((accessor) => {
            this.setId(accessor.addZone(this));
            this.adjustHeightAndLayout();
        });
        this.resizeObserver = new ResizeObserver(() => {
            this.adjustHeightAndLayout();
        });
        this.resizeObserver.observe(this.linkedContentDomNode);
    }

    private adjustHeightAndLayout(): void {
        this.editor.changeViewZones((accessor) => {
            this.heightInPx = this.linkedContentDomNode.offsetHeight;
            accessor.layoutZone(this.getId());
        });
    }

    removeFromEditor(): void {
        this.editor.changeViewZones((accessor) => {
            accessor.removeZone(this.getId());
        });
        this.resizeObserver.disconnect();
    }

    /**
     * From IViewZone. Called automatically when the position of this view zone in the editor is computed.
     *
     * @param top The vertical offset (in pixels) of this view zone within the editor.
     */
    onDomNodeTop(top: number): void {
        this.linkedContentDomNode.style.top = top + 'px';
    }
}
