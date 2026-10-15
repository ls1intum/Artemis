import { Component, DestroyRef, ElementRef, HostListener, Renderer2, ViewEncapsulation, afterNextRender, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { InteractableEvent } from 'app/programming/manage/code-editor/file-browser/code-editor-file-browser.component';
import { CollapsableCodeEditorElement } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { ResizeType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ResizableDirective } from 'app/shared-ui/directives/resizable.directive';
import { FeedbackSplitComponent } from 'app/exercise/feedback-bottom-panel/feedback-split.component';
import { SplitterModule } from 'primeng/splitter';
import type { SplitterResizeEndEvent } from 'primeng/types/splitter';
import { SPLITTER_GUTTER_SIZE, splitterPassThrough } from 'app/assessment/manage/assessment-workspace/splitter-pass-through';
import { NgTemplateOutlet } from '@angular/common';

@Component({
    selector: 'jhi-code-editor-grid',
    templateUrl: './code-editor-grid.component.html',
    styleUrls: ['./code-editor-grid.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [ResizableDirective, FeedbackSplitComponent, NgTemplateOutlet, SplitterModule],
    host: { '[class.code-editor-grid--fill]': 'fillHeight()' },
})
export class CodeEditorGridComponent {
    private renderer = inject(Renderer2);
    private readonly destroyRef = inject(DestroyRef);

    readonly editorWrapperElement = viewChild<ElementRef<HTMLElement>>('editorWrapper');
    readonly buildOutputElement = viewChild.required<ElementRef>('buildOutput');
    readonly fileBrowserElement = viewChild.required<ElementRef>('fileBrowser');
    readonly instructionsElement = viewChild.required<ElementRef>('instructions');

    readonly isTutorAssessment = input(false);
    /**
     * Whether the grid lays out a tutor's assessment workspace, filling the page like the text and modeling assessments.
     * Pages that only show a tutor's feedback, such as a student's assessed submission, set {@link isTutorAssessment} alone.
     */
    readonly assessmentWorkspace = input(false);
    readonly showEditorNavbar = input(true);
    readonly showEditorSidebarRight = input(true);
    /**
     * Whether the bottom area has its own grip below it. The panel that shares the build output with the general feedback
     * leaves it out; the grip between the editor and the bottom area still sets its height.
     */
    readonly bottomResizable = input(true);
    /**
     * Whether the grid fills the height of its host instead of using fixed panel heights: the file browser and the editor
     * take the upper part, the bottom area the rest, and the page around them does not scroll. The grip between them still
     * moves the split.
     */
    readonly fillHeight = input(false);
    readonly onResize = output<ResizeType>();

    readonly fileBrowserIsCollapsed = signal(false);
    readonly rightPanelIsCollapsed = signal(false);
    readonly buildOutputIsCollapsed = signal(false);

    // Resizable constraints (px). The min values mirror the previous interact.js configuration,
    // which derived them from the screen dimensions at view init.
    readonly resizableMinHeightMain = window.screen.height / 3;
    readonly resizableMinHeightBottom = window.screen.height / 6;

    // Small safety buffer for the inter-panel gap / grip / margins, so a resize cannot collapse a neighbour to nothing.
    private static readonly VERTICAL_BUFFER_PX = 40;

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
    });

    protected readonly ResizeType = ResizeType;

    protected readonly gutterSize = SPLITTER_GUTTER_SIZE;
    protected readonly passThrough = splitterPassThrough('horizontal');
    /** The widths (percent) of the file browser, the editor and the instructions; the splitter remembers what the user drags them to. */
    protected readonly sidebarSizes = computed(() => (this.assessmentWorkspace() ? [20, 45, 35] : [24, 46, 30]));
    /**
     * The smallest widths (percent) the splitter lets the three panels shrink to. An assessment keeps the file browser and the editor
     * at 36% or more, like the text and modeling assessment workspace, whose instructions side may be dragged to between 24% and 64%.
     */
    protected readonly sidebarMinSizes = computed(() => (this.assessmentWorkspace() ? [10, 36, 24] : [10, 30, 10]));
    private lastSidebarSizes?: number[];

    constructor() {
        afterNextRender(() => {
            this.recomputeMaxConstraints();
            this.observeLayout();
        });
    }

    @HostListener('window:resize')
    onWindowResize(): void {
        this.recomputeMaxConstraints();
    }

    /** The user finished dragging a divider between the file browser, the editor and the instructions. */
    protected onSidebarsResized(event: SplitterResizeEndEvent): void {
        // The splitter reports the new sizes only; the divider next to the file browser moved when the file browser's own share changed
        const fileBrowserMoved = this.lastSidebarSizes !== undefined && Math.abs(Number(event.sizes[0]) - this.lastSidebarSizes[0]) > 0.01;
        this.lastSidebarSizes = event.sizes.map(Number);
        this.onResize.emit(fileBrowserMoved ? ResizeType.SIDEBAR_LEFT : ResizeType.SIDEBAR_RIGHT);
    }

    /** Re-emit a panel resize and refresh the sum-aware maxima so the next drag accounts for the new sizes. */
    protected onPanelResized(type: ResizeType): void {
        this.recomputeMaxConstraints();
        this.onResize.emit(type);
    }

    /**
     * Measures the maxima again whenever the area of the panels changes its size, which includes the moment it is shown. The exam keeps the
     * pages the student has left in the document, hidden: a resize of the window measures nothing there, and the editor is shown again at a size
     * that was never measured, so the maxima of the old size would clamp every drag of a divider.
     */
    private observeLayout(): void {
        const content = this.editorWrapperElement()?.nativeElement.querySelector('.editor-main__content');
        if (!content || typeof ResizeObserver === 'undefined') {
            return;
        }
        const observer = new ResizeObserver(() => this.recomputeMaxConstraints());
        observer.observe(content);
        this.destroyRef.onDestroy(() => observer.disconnect());
    }

    /**
     * Recomputes the sum-aware panel maxima from the current layout: each panel may grow only into the space left
     * by its neighbour and the editor, within the visible viewport. Called on init, window resize, resize end,
     * collapse and when the area of the panels changes its size (see {@link observeLayout}), so it never runs during typing.
     */
    private recomputeMaxConstraints(): void {
        const wrapper = this.editorWrapperElement()?.nativeElement;
        if (!wrapper) {
            return;
        }
        const main = wrapper.querySelector<HTMLElement>('.editor-main');
        const bottom = wrapper.querySelector<HTMLElement>('.editor-bottom');
        const content = wrapper.querySelector<HTMLElement>('.editor-main__content');
        if (content?.clientWidth === 0) {
            // not rendered (a hidden page of the exam): there is nothing to measure, and the layout is measured when it is shown
            return;
        }

        const mainTop = (main ?? wrapper).getBoundingClientRect().top;
        const availableHeight = Math.max(0, window.innerHeight - mainTop - CodeEditorGridComponent.VERTICAL_BUFFER_PX);

        // A filling grid gives the bottom area whatever the editor leaves, so the editor may grow until the bottom area is
        // at its minimum rather than only into the bottom area's current height
        const heightMain = this.fillHeight()
            ? Math.max(this.resizableMinHeightMain, wrapper.getBoundingClientRect().bottom - mainTop - CodeEditorGridComponent.VERTICAL_BUFFER_PX - this.resizableMinHeightBottom)
            : Math.max(this.resizableMinHeightMain, Math.min(1200, availableHeight - (bottom?.offsetHeight ?? this.resizableMinHeightBottom)));

        this.maxConstraints.set({
            heightMain,
            heightBottom: Math.max(this.resizableMinHeightBottom, Math.min(600, availableHeight - (main?.offsetHeight ?? this.resizableMinHeightMain))),
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
