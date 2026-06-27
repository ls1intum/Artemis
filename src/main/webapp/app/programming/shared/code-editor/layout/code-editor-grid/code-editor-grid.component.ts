import { Component, ElementRef, HostListener, Renderer2, ViewEncapsulation, afterNextRender, inject, input, output, signal, viewChild } from '@angular/core';
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

    readonly editorWrapperElement = viewChild<ElementRef<HTMLElement>>('editorWrapper');
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

    // Width that must always remain for the code editor in the middle, and small safety buffers for the
    // inter-panel gap / grip / margins, so a resize cannot collapse a neighbour to nothing.
    private static readonly EDITOR_CENTER_MIN_WIDTH = 300;
    private static readonly VERTICAL_BUFFER_PX = 40;
    private static readonly HORIZONTAL_BUFFER_PX = 24;

    /**
     * Maximum panel sizes (px), recomputed on layout-affecting events (see {@link recomputeMaxConstraints}) rather
     * than per change-detection, so the editor does not pay a layout read on every keystroke. The maxima are
     * sum-aware: each panel's max leaves room for its neighbour and the editor, so editor + build output (height) or
     * file browser + editor + instructions (width) can never exceed the visible area and push a panel - or its resize
     * grip - off the clipped wrapper (which previously left it unrecoverable without a reload). interact.js bounded
     * this implicitly via its parent restriction; the in-house directive only clamps to explicit px limits.
     */
    protected readonly maxConstraints = signal({
        heightMain: 1200,
        heightBottom: 600,
        widthLeft: window.screen.width / 2,
        widthRight: window.screen.width / 1.3,
    });

    protected readonly ResizeType = ResizeType;

    // Icons
    faGripLines = faGripLines;
    faGripLinesVertical = faGripLinesVertical;

    constructor() {
        afterNextRender(() => this.recomputeMaxConstraints());
    }

    @HostListener('window:resize')
    onWindowResize(): void {
        this.recomputeMaxConstraints();
    }

    /** Re-emit a panel resize and refresh the sum-aware maxima so the next drag accounts for the new sizes. */
    protected onPanelResized(type: ResizeType): void {
        this.recomputeMaxConstraints();
        this.onResize.emit(type);
    }

    /**
     * Recomputes the sum-aware panel maxima from the current layout: each panel may grow only into the space left
     * by its neighbour and the editor, within the visible viewport. Called on init, window resize, resize end and
     * collapse, so it never runs during typing.
     */
    private recomputeMaxConstraints(): void {
        const wrapper = this.editorWrapperElement()?.nativeElement;
        if (!wrapper) {
            return;
        }
        const main = wrapper.querySelector<HTMLElement>('.editor-main');
        const bottom = wrapper.querySelector<HTMLElement>('.editor-bottom');
        const content = wrapper.querySelector<HTMLElement>('.editor-main__content');
        const left = wrapper.querySelector<HTMLElement>('.editor-sidebar-left');
        const right = wrapper.querySelector<HTMLElement>('.editor-sidebar-right');

        const mainTop = (main ?? wrapper).getBoundingClientRect().top;
        const availableHeight = Math.max(0, window.innerHeight - mainTop - CodeEditorGridComponent.VERTICAL_BUFFER_PX);
        const availableWidth = content?.clientWidth ?? window.innerWidth;
        const reservedWidth = CodeEditorGridComponent.EDITOR_CENTER_MIN_WIDTH + CodeEditorGridComponent.HORIZONTAL_BUFFER_PX;

        this.maxConstraints.set({
            heightMain: Math.max(this.resizableMinHeightMain, Math.min(1200, availableHeight - (bottom?.offsetHeight ?? this.resizableMinHeightBottom))),
            heightBottom: Math.max(this.resizableMinHeightBottom, Math.min(600, availableHeight - (main?.offsetHeight ?? this.resizableMinHeightMain))),
            widthLeft: Math.max(this.resizableMinWidthLeft, Math.min(window.screen.width / 2, availableWidth - (right?.offsetWidth ?? 0) - reservedWidth)),
            widthRight: Math.max(this.resizableMinWidthRight, Math.min(window.screen.width / 1.3, availableWidth - (left?.offsetWidth ?? 0) - reservedWidth)),
        });
    }

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
        // The collapse buttons emit a plain DOM event; blur the clicked control so it doesn't keep focus styling.
        // (The old `event.event?.toElement || event.relatedTarget` chain was interact.js's wrapped-event shape and
        // is dead now that interact.js is gone.)
        const target = event.target as HTMLElement | undefined;
        target?.blur();
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

        // A collapse changes the space available to the other panels; refresh the sum-aware maxima.
        this.recomputeMaxConstraints();
    }
}
