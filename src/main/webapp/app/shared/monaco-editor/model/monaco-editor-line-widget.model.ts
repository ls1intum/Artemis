import * as monaco from 'monaco-editor';

type ViewZone = monaco.editor.IViewZone;
export class MonacoEditorLineWidget implements monaco.editor.IOverlayWidget, monaco.IDisposable {
    private readonly id: string;
    private readonly domNode: HTMLElement;
    private resizeObserver: ResizeObserver;
    private readonly lineNumber: number;

    /**
     * Creates an overlay widget that contains the specified DOM node and renders it in a dedicated ViewZone.
     * The DOM node will be set to visible using the display: unset attribute.
     * @param id The unique ID of this overlay widget.
     * @param domNode The content of this overlay widget.
     * @param lineNumber The line after which the ViewZone should be inserted.
     * @param registerViewZone A callback registering the ViewZone in the editor.
     * @param layoutViewZone A callback layouting the ViewZone in the editor (in case of resizing).
     */
    constructor(id: string, domNode: HTMLElement, lineNumber: number, registerViewZone: (viewZone: ViewZone) => string, layoutViewZone: (viewZoneId: string) => void) {
        this.id = id;
        this.domNode = domNode;
        this.lineNumber = lineNumber;
        this.setupViewZone(registerViewZone, layoutViewZone);
        this.ensureVisible();
    }

    private ensureVisible() {
        this.domNode.style.display = 'unset';
        this.domNode.style.width = '100%';
    }

    private setupViewZone(registerViewZone: (viewZone: ViewZone) => string, layoutViewZone: (viewZoneId: string) => void) {
        const viewZoneDom = document.createElement('div');
        const overlayWidgetDom = this.getDomNode();
        const viewZone: ViewZone = {
            afterLineNumber: this.lineNumber,
            domNode: viewZoneDom,
            onDomNodeTop: (top: number) => {
                // Links the position of the ViewZone and this widget together.
                overlayWidgetDom.style.top = top + 'px';
            },
            get heightInPx() {
                // Forces the height of the ViewZone to fit this widget.
                return overlayWidgetDom.offsetHeight + 2;
            },
        };

        const viewZoneId = registerViewZone(viewZone);
        this.resizeObserver = new ResizeObserver(() => layoutViewZone(viewZoneId));
        this.resizeObserver.observe(overlayWidgetDom);
    }

    dispose(): void {
        this.resizeObserver.disconnect();
    }

    getId(): string {
        return this.id;
    }
    getDomNode(): HTMLElement {
        return this.domNode;
    }
    getPosition(): null {
        return null;
    }
}
