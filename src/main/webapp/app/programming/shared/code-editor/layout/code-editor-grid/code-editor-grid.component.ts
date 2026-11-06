import {
    Component,
    DestroyRef,
    ElementRef,
    HostListener,
    OnInit,
    Renderer2,
    ViewEncapsulation,
    afterNextRender,
    computed,
    inject,
    input,
    output,
    signal,
    viewChild,
} from '@angular/core';
import { InteractableEvent } from 'app/programming/manage/code-editor/file-browser/code-editor-file-browser.component';
import { CollapsableCodeEditorElement } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { ResizeType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ResizableDirective } from 'app/shared-ui/directives/resizable.directive';
import { FeedbackSplitComponent } from 'app/exercise/feedback-bottom-panel/feedback-split.component';
import { TumAetUiSplitterComponent } from '@tumaet/ui-angular';
import { parseJson } from 'app/foundation/util/json.util';
import { NgTemplateOutlet } from '@angular/common';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { CollapsedPanelRailComponent, CollapsedPanelRailItem } from 'app/shared-ui/components/collapsed-panel-rail/collapsed-panel-rail.component';

@Component({
    selector: 'jhi-code-editor-grid',
    templateUrl: './code-editor-grid.component.html',
    styleUrls: ['./code-editor-grid.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [ResizableDirective, FeedbackSplitComponent, NgTemplateOutlet, TumAetUiSplitterComponent, CollapsedPanelRailComponent],
    host: { '[class.code-editor-grid--fill]': 'fillHeight()' },
})
export class CodeEditorGridComponent implements OnInit {
    private renderer = inject(Renderer2);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly CollapsableCodeEditorElement = CollapsableCodeEditorElement;
    /** The rail of an assessment's collapsed instructions column. */
    protected readonly defaultInstructionsRailItems: CollapsedPanelRailItem[] = [{ icon: faListAlt, labelKey: 'artemisApp.exercise.instructions' }];

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
    /** One icon for each tab of an assessment's instructions, which its collapsed right column shows. */
    readonly instructionsRailItems = input<CollapsedPanelRailItem[]>();
    /** The id of the instructions tab whose icon opened the collapsed column. */
    readonly instructionsRailItemSelected = output<string>();
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
    /** Whether the bottom area shares the height with the rest by a splitter instead of a grip: a filling grid and an assessment. */
    protected readonly fillLayout = computed(() => this.fillHeight() || this.assessmentWorkspace());
    readonly onResize = output<ResizeType>();
    /** The file browser was dragged so narrow that it should collapse to its strip; the page that owns it collapses it. */
    readonly collapseFileBrowserRequest = output<void>();
    /** The instructions were dragged so narrow that they should collapse to their rail; the page that owns them collapses them. */
    readonly collapseInstructionsRequest = output<void>();

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

    /**
     * The file browser beside the editor (inner), and that area beside the instructions (outer). The kit's splitter divides two panels, so
     * the three columns are two splitters, on the exercise page as in an assessment, where the bottom area lies under the inner one.
     * These are the widths (percent) the user last chose; until then the defaults apply.
     */
    private readonly chosenInnerSizes = signal<number[] | undefined>(undefined);
    private readonly chosenOuterSizes = signal<number[] | undefined>(undefined);
    protected readonly innerSizes = computed(() => this.chosenInnerSizes() ?? (this.assessmentWorkspace() ? [30, 70] : [34, 66]));
    protected readonly outerSizes = computed(() => this.chosenOuterSizes() ?? (this.assessmentWorkspace() ? [65, 35] : [70, 30]));
    /** The editor, and the area it sits in beside the instructions, keep a share of the width, like the text and modeling assessment workspace. */
    protected readonly innerMinSizes = [5, 36];
    protected readonly outerMinSizes = [36, 5];
    /**
     * The width (px) at or below which dragging the file browser or the instructions narrower collapses them. A width rather than a share, so that the
     * panel collapses at the same point on a narrow page (the exercise's split panel) and on a wide one (the assessment).
     */
    private static readonly SIDEBAR_SNAP_PX = 140;
    /** Where the chosen widths are remembered between visits. */
    private readonly innerStorageKey = computed(() => (this.assessmentWorkspace() ? 'code-editor-assessment-inner' : 'code-editor-sidebars-inner'));
    private readonly outerStorageKey = computed(() => (this.assessmentWorkspace() ? 'code-editor-assessment-outer' : 'code-editor-sidebars-outer'));

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

    ngOnInit(): void {
        this.chosenInnerSizes.set(this.readSavedSizes(this.innerStorageKey()));
        this.chosenOuterSizes.set(this.readSavedSizes(this.outerStorageKey()));
    }

    /** The user finished dragging the divider between the file browser and the editor. */
    protected onInnerResizeEnd(sizes: number[]): void {
        if (this.fileBrowserElement().nativeElement.getBoundingClientRect().width <= CodeEditorGridComponent.SIDEBAR_SNAP_PX) {
            // Dragged shut: the rail stands in for the panel, and the sliver is not what it reopens with, so the splitter goes back to the
            // widths chosen before
            this.chosenInnerSizes.set([...this.innerSizes()]);
            this.collapseFileBrowserRequest.emit();
        } else {
            this.chosenInnerSizes.set(sizes);
            this.saveSizes(this.innerStorageKey(), sizes);
        }
        this.onResize.emit(ResizeType.SIDEBAR_LEFT);
    }

    /** The user finished dragging the divider between the area of the file browser and the editor, and the instructions. */
    protected onOuterResizeEnd(sizes: number[]): void {
        if (this.instructionsElement().nativeElement.getBoundingClientRect().width <= CodeEditorGridComponent.SIDEBAR_SNAP_PX) {
            this.chosenOuterSizes.set([...this.outerSizes()]);
            if (this.assessmentWorkspace()) {
                // An assessment's grid collapses the column itself, to its rail
                this.toggleCollapse({ event: new MouseEvent('click'), horizontal: true }, CollapsableCodeEditorElement.Instructions);
            } else {
                this.collapseInstructionsRequest.emit();
            }
        } else {
            this.chosenOuterSizes.set(sizes);
            this.saveSizes(this.outerStorageKey(), sizes);
        }
        this.onResize.emit(ResizeType.SIDEBAR_RIGHT);
    }

    private readSavedSizes(key: string): number[] | undefined {
        try {
            const raw = localStorage.getItem(key);
            const parsed = raw === null ? undefined : parseJson<unknown>(raw);
            if (Array.isArray(parsed) && parsed.length === 2 && parsed.every((size) => typeof size === 'number' && size > 0)) {
                return parsed;
            }
        } catch {
            // Not JSON, or storage is blocked: the default widths apply
        }
        return undefined;
    }

    private saveSizes(key: string, sizes: number[]): void {
        try {
            localStorage.setItem(key, JSON.stringify(sizes));
        } catch {
            // Storage is blocked; the widths still apply for this visit
        }
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

    /** Collapses the build output, e.g. when the splitter above it was dragged down to its smallest size; a collapsed one stays so. */
    collapseBuildOutput() {
        if (this.buildOutputIsCollapsed()) {
            return;
        }
        // The same state as the collapse button leaves: the class the card styles read, and the signal
        this.renderer.addClass(this.buildOutputElement().nativeElement, 'collapsed--vertical');
        this.buildOutputIsCollapsed.set(true);
        this.recomputeMaxConstraints();
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
