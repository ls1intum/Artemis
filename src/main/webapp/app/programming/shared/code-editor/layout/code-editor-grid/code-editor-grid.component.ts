import { Component, ElementRef, Renderer2, ViewEncapsulation, inject, input, output, signal, viewChild } from '@angular/core';
import { InteractableEvent } from 'app/programming/manage/code-editor/file-browser/code-editor-file-browser.component';
import { faGripLines, faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { CollapsableCodeEditorElement } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ResizeType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ResizableDirective } from 'app/shared-ui/directives/resizable.directive';

@Component({
    selector: 'jhi-code-editor-grid',
    templateUrl: './code-editor-grid.component.html',
    styleUrls: ['./code-editor-grid.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [FaIconComponent, ResizableDirective],
})
export class CodeEditorGridComponent {
    private renderer = inject(Renderer2);

    readonly buildOutputElement = viewChild.required<ElementRef>('buildOutput');
    readonly fileBrowserElement = viewChild.required<ElementRef>('fileBrowser');
    readonly instructionsElement = viewChild.required<ElementRef>('instructions');

    readonly isTutorAssessment = input(false);
    readonly showEditorNavbar = input(true);
    readonly showEditorSidebarRight = input(true);
    readonly onResize = output<ResizeType>();

    readonly fileBrowserIsCollapsed = signal(false);
    readonly rightPanelIsCollapsed = signal(false);
    readonly buildOutputIsCollapsed = signal(false);

    // Resizable constraints (px). The min values mirror the previous interact.js configuration,
    // which derived them from the screen dimensions at view init.
    readonly resizableMinHeightMain = window.screen.height / 3;
    readonly resizableMinWidthLeft = window.screen.width / 7;
    readonly resizableMinWidthRight = window.screen.width / 6;
    readonly resizableMinHeightBottom = window.screen.height / 6;

    // Reserve enough of the viewport for the neighbouring panel so a resize can never push the build output (height)
    // or the code editor (width) fully off the clipped wrapper - which previously left them unrecoverable without a
    // reload. interact.js implicitly bounded this via its parent restriction; the in-house directive only clamps to
    // these px limits, so the maxima are capped against the live viewport (not the larger physical screen).
    private static readonly RESERVED_VERTICAL_PX = 320;
    private static readonly RESERVED_HORIZONTAL_PX = 360;

    get resizableMaxHeightMain(): number {
        return Math.max(this.resizableMinHeightMain, Math.min(1200, window.innerHeight - CodeEditorGridComponent.RESERVED_VERTICAL_PX));
    }

    get resizableMaxWidthLeft(): number {
        return Math.max(this.resizableMinWidthLeft, Math.min(window.screen.width / 2, window.innerWidth - CodeEditorGridComponent.RESERVED_HORIZONTAL_PX));
    }

    get resizableMaxWidthRight(): number {
        return Math.max(this.resizableMinWidthRight, Math.min(window.screen.width / 1.3, window.innerWidth - CodeEditorGridComponent.RESERVED_HORIZONTAL_PX));
    }

    get resizableMaxHeightBottom(): number {
        return Math.max(this.resizableMinHeightBottom, Math.min(600, window.innerHeight - CodeEditorGridComponent.RESERVED_VERTICAL_PX));
    }

    protected readonly ResizeType = ResizeType;

    // Icons
    faGripLines = faGripLines;
    faGripLinesVertical = faGripLinesVertical;

    private elementRefForCollapsableElement(collapsableElement: CollapsableCodeEditorElement): ElementRef {
        switch (collapsableElement) {
            case CollapsableCodeEditorElement.BuildOutput:
                return this.buildOutputElement();
            case CollapsableCodeEditorElement.FileBrowser:
                return this.fileBrowserElement();
            case CollapsableCodeEditorElement.Instructions:
                return this.instructionsElement();
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
        const target = event.event?.toElement || event.relatedTarget || event.target;
        target.blur();
        const cardElement = this.elementRefForCollapsableElement(collapsableElement);

        const collapsed = `collapsed--${horizontal ? 'horizontal' : 'vertical'}`;

        if (cardElement.nativeElement.classList.contains(collapsed)) {
            this.renderer.removeClass(cardElement.nativeElement, collapsed);
        } else {
            this.renderer.addClass(cardElement.nativeElement, collapsed);
        }

        // Toggle the collapse signals. They both hide the draggable grip icons and disable the
        // matching panel's resizing (bound via [resizableEnabled] in the template).
        switch (collapsableElement) {
            case CollapsableCodeEditorElement.Instructions: {
                this.rightPanelIsCollapsed.update((value) => !value);
                break;
            }
            case CollapsableCodeEditorElement.FileBrowser: {
                this.fileBrowserIsCollapsed.update((value) => !value);
                break;
            }
            case CollapsableCodeEditorElement.BuildOutput: {
                this.buildOutputIsCollapsed.update((value) => !value);
                break;
            }
        }
    }
}
