import { AfterViewInit, Component, ContentChild, ElementRef, EventEmitter, Input, Output, Renderer2, ViewChild, ViewEncapsulation } from '@angular/core';
import { Interactable } from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { ResizeType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { InteractableEvent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { faGripLines, faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { CollapsableCodeEditorElement } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';

@Component({
    selector: 'jhi-code-editor-grid',
    templateUrl: './code-editor-grid.component.html',
    styleUrls: ['./code-editor-grid.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class CodeEditorGridComponent implements AfterViewInit {
    @ContentChild('editorSidebarRight', { static: false }) editorSidebarRight: ElementRef;
    @ContentChild('editorSidebarLeft', { static: false }) editorSidebarLeft: ElementRef;
    @ContentChild('editorBottomArea', { static: false }) editorBottomArea: ElementRef;

    @ViewChild('buildOutput') buildOutputElement: ElementRef;
    @ViewChild('fileBrowser') fileBrowserElement: ElementRef;
    @ViewChild('instructions') instructionsElement: ElementRef;

    @Input() isTutorAssessment = false;
    @Output() onResize = new EventEmitter<ResizeType>();

    fileBrowserIsCollapsed = false;
    rightPanelIsCollapsed = false;
    buildOutputIsCollapsed = false;

    interactResizableMain: Interactable;
    resizableMinHeightMain = 480;
    resizableMaxHeightMain = 1200;

    interactResizableLeft: Interactable;
    resizableMinWidthLeft: number;
    resizableMaxWidthLeft = 2000;

    interactResizableRight: Interactable;
    resizableMinWidthRight: number;
    resizableMaxWidthRight = 2000;

    interactResizableBottom: Interactable;
    resizableMinHeightBottom = 300;
    resizableMaxHeightBottom = 600;

    // Icons
    faGripLines = faGripLines;
    faGripLinesVertical = faGripLinesVertical;

    constructor(private renderer: Renderer2) {}

    /**
     * After the view was initialized, we create an interact.js resizable object,
     * designate the edges which can be used to resize the target element and set min and max values.
     * The 'resizemove' callback function processes the event values and sets new width and height values for the element.
     */
    ngAfterViewInit(): void {
        this.resizableMinHeightMain = window.screen.height / 3;
        this.interactResizableMain = interact('.editor-main')
            .resizable({
                // Enable resize from bottom edge; triggered by class rg-bottom
                edges: { left: false, right: false, bottom: '.rg-main-bottom', top: false },
                // Set min and max height
                modifiers: [
                    interact.modifiers!.restrictSize({
                        min: { width: 0, height: this.resizableMinHeightMain },
                        max: { width: 2000, height: this.resizableMaxHeightMain },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
                this.onResize.emit(ResizeType.MAIN_BOTTOM);
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                // Update element height
                target.style.height = event.rect.height + 'px';
            });

        this.resizableMinWidthLeft = window.screen.width / 7;
        this.resizableMaxWidthLeft = window.screen.width / 2;
        this.interactResizableLeft = interact('.editor-sidebar-left')
            .resizable({
                // Enable resize from bottom edge; triggered by class rg-bottom
                edges: { left: false, right: '.rg-sidebar-left', bottom: false, top: false },
                // Set min and max height
                modifiers: [
                    interact.modifiers!.restrictSize({
                        min: { width: this.resizableMinWidthLeft, height: 0 },
                        max: { width: this.resizableMaxWidthLeft, height: 2000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
                this.onResize.emit(ResizeType.SIDEBAR_LEFT);
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                // Update element height
                target.style.width = event.rect.width + 'px';
            });

        this.resizableMinWidthRight = window.screen.width / 6;
        this.resizableMaxWidthRight = window.screen.width / 1.3;
        this.interactResizableRight = interact('.editor-sidebar-right')
            .resizable({
                // Enable resize from bottom edge; triggered by class rg-bottom
                edges: { left: '.rg-sidebar-right', right: false, bottom: false, top: false },
                // Set min and max height
                modifiers: [
                    interact.modifiers!.restrictSize({
                        min: { width: this.resizableMinWidthRight, height: 0 },
                        max: { width: this.resizableMaxWidthRight, height: 2000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
                this.onResize.emit(ResizeType.SIDEBAR_RIGHT);
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                // Update element height
                target.style.width = event.rect.width + 'px';
            });

        this.resizableMinHeightBottom = window.screen.height / 6;
        this.interactResizableBottom = interact('.editor-bottom')
            .resizable({
                // Enable resize from bottom edge; triggered by class rg-bottom
                edges: { left: false, right: false, bottom: '.rg-bottom-bottom', top: false },
                // Set min and max height
                modifiers: [
                    interact.modifiers!.restrictSize({
                        min: { width: 0, height: this.resizableMinHeightBottom },
                        max: { width: window.screen.width, height: this.resizableMaxHeightBottom },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
                this.onResize.emit(ResizeType.BOTTOM);
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                // Update element height
                target.style.height = event.rect.height + 'px';
            });
    }

    private elementRefForCollapsableElement(collapsableElement: CollapsableCodeEditorElement): ElementRef {
        switch (collapsableElement) {
            case CollapsableCodeEditorElement.BuildOutput:
                return this.buildOutputElement;
            case CollapsableCodeEditorElement.FileBrowser:
                return this.fileBrowserElement;
            case CollapsableCodeEditorElement.Instructions:
                return this.instructionsElement;
        }
    }

    /**
     * Collapse parts of the editor (file browser, build output, or instructions)
     * @param interactableEvent {object} The custom event object with additional information
     * @param collapsableElement an enum to decide which card is collapsed
     */
    toggleCollapse(interactableEvent: InteractableEvent, collapsableElement: CollapsableCodeEditorElement) {
        const event = interactableEvent.event;
        const horizontal = interactableEvent.horizontal;
        const interactResizable = interactableEvent.interactable;
        const target = event.event?.toElement || event.relatedTarget || event.target;
        target.blur();
        const cardElement = this.elementRefForCollapsableElement(collapsableElement);

        const collapsed = `collapsed--${horizontal ? 'horizontal' : 'vertical'}`;

        if (cardElement.nativeElement.classList.contains(collapsed)) {
            this.renderer.removeClass(cardElement.nativeElement, collapsed);
            interactResizable.resizable({ enabled: true });
        } else {
            this.renderer.addClass(cardElement.nativeElement, collapsed);
            interactResizable.resizable({ enabled: false });
        }

        // used to disable draggable icons
        switch (interactResizable.target) {
            case '.resizable-instructions': {
                this.rightPanelIsCollapsed = !this.rightPanelIsCollapsed;
                break;
            }
            case '.resizable-filebrowser': {
                this.fileBrowserIsCollapsed = !this.fileBrowserIsCollapsed;
                break;
            }
            case '.resizable-buildoutput': {
                this.buildOutputIsCollapsed = !this.buildOutputIsCollapsed;
                break;
            }
        }
    }
}
