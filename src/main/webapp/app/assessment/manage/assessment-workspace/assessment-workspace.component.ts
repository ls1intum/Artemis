import { AfterViewInit, Component, ElementRef, OnDestroy, computed, inject, input, output, signal } from '@angular/core';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { SplitterModule } from 'primeng/splitter';
import { SPLITTER_GUTTER_SIZE, splitterPassThrough } from 'app/assessment/manage/assessment-workspace/splitter-pass-through';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CollapsedPanelRailComponent, CollapsedPanelRailItem } from 'app/shared-ui/components/collapsed-panel-rail/collapsed-panel-rail.component';
import { FeedbackBottomPanelComponent } from 'app/exercise/feedback-bottom-panel/feedback-bottom-panel.component';
import { FeedbackSplitComponent } from 'app/exercise/feedback-bottom-panel/feedback-split.component';

const STACK_PRIMARY_AT = 840;
/** What the splitter gives collapsed instructions. Not 0: PrimeNG reads a size of 0 as "no size given". The rail keeps its own size, see the styles. */
const COLLAPSED_SIZES = [99.99, 0.01];
/**
 * The smallest share (percent) the area of the canvas and the details keeps, and the smallest the instructions may be dragged to. PrimeNG
 * applies the first to the panel before the divider and the second to the one after it, which is the case of this splitter's two panels.
 */
const AREA_MIN_PERCENT = 36;
const INSTRUCTIONS_MIN_PERCENT = 8;
/** The share (percent) of the instructions at or below which dragging them narrower collapses them to their rail. */
const INSTRUCTIONS_SNAP_PERCENT = 15;
/**
 * Stacked, the instructions are sized along the height, and a panel is only limited in height: it collapses to its rail only when it is sized
 * along the width, as the right panel of the exercise details does. This is the smallest share (percent) they may be dragged to then.
 */
const HEIGHT_MIN_PERCENT = 24;

/**
 * The workspace of an assessment of a text or modeling submission, laid out like the page of a student: the work (`[assessmentWorkspaceCanvas]`)
 * with the general feedback and notes panel under it (`[assessmentWorkspaceDetails]`, headed by `[assessmentWorkspaceDetailsHeader]`), and the
 * instructions (`[assessmentWorkspaceInstructions]`) beside them.
 *
 * The instructions collapse to an icon rail when they are dragged narrow. The details panel is limited in height, and collapses from its
 * button into a rail with one icon for each tab (`[detailsRailItems]`), which opens the panel at that tab (`detailsItemSelected`).
 */
@Component({
    selector: 'jhi-assessment-workspace',
    templateUrl: './assessment-workspace.component.html',
    styleUrls: ['./assessment-workspace.component.scss'],
    imports: [
        CdkScrollable,
        FaIconComponent,
        NgTemplateOutlet,
        SplitterModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        CollapsedPanelRailComponent,
        FeedbackBottomPanelComponent,
        FeedbackSplitComponent,
    ],
})
export class AssessmentWorkspaceComponent implements AfterViewInit, OnDestroy {
    private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly width = signal(Number.POSITIVE_INFINITY);
    private resizeObserver?: ResizeObserver;

    readonly detailsLabelKey = input('artemisApp.assessment.feedbackAndNotes');
    readonly showDetails = input(true);
    /** Whether the page fills the details header itself through `[assessmentWorkspaceDetailsHeader]`, e.g. with tabs. */
    readonly customDetailsHeader = input(false);
    /** Whether the page fills the instructions header itself through `[assessmentWorkspaceInstructionsHeader]`, e.g. with tabs. */
    readonly customInstructionsHeader = input(false);
    readonly storageKey = input.required<string>();
    /** One icon for each tab of the details panel, which its rail shows once it is collapsed, so the rail tells what the panel holds. */
    readonly detailsRailItems = input<CollapsedPanelRailItem[]>();

    /** One icon for each tab of the instructions, which the rail of the collapsed instructions shows, like the rail of the details. */
    readonly instructionsRailItems = input<CollapsedPanelRailItem[]>();
    /** The id of the instructions tab whose icon opened the collapsed instructions; the page opens the tab. */
    readonly instructionsItemSelected = output<string>();

    /** The id of the details tab whose icon opened the collapsed panel; the page opens the tab. */
    readonly detailsItemSelected = output<string>();

    protected readonly detailsStorageKey = computed(() => `${this.storageKey()}-details`);
    protected readonly primaryVertical = computed(() => this.width() < STACK_PRIMARY_AT);
    protected readonly primaryLayout = computed(() => (this.primaryVertical() ? 'vertical' : 'horizontal'));
    /** The split the user last chose between the area of the canvas and the instructions, which collapsed instructions open with again. */
    private readonly chosenSizes = signal<number[] | undefined>(undefined);
    private sizesBeforeDrag?: number[];
    protected readonly detailsExpanded = signal(true);
    protected readonly instructionsCollapsed = signal(false);
    // A copy: the splitter adopts the bound array and changes it in place on every drag
    protected readonly primarySizes = computed(() => [
        ...(this.instructionsCollapsed() ? COLLAPSED_SIZES : (this.chosenSizes() ?? (this.primaryVertical() ? [60, 40] : [65, 35]))),
    ]);
    protected readonly primaryMinSizes = computed(() =>
        this.instructionsCollapsed() ? [0, 0] : [AREA_MIN_PERCENT, this.primaryVertical() ? HEIGHT_MIN_PERCENT : INSTRUCTIONS_MIN_PERCENT],
    );
    protected readonly railOrientation = computed(() => (this.primaryVertical() ? 'horizontal' : 'vertical'));
    // The dividers have the colour of the panels, as everywhere in an assessment
    protected readonly primaryPassThrough = computed(() => splitterPassThrough(this.primaryLayout(), {}, 'panel'));
    protected readonly gutterSize = SPLITTER_GUTTER_SIZE;
    protected readonly faInstructions = faListAlt;
    protected readonly faChevronRight = faChevronRight;

    protected onResizeStart(event: { sizes: (number | string)[] }): void {
        this.sizesBeforeDrag = event.sizes.map(Number);
    }

    /** Dragging the instructions down to a sliver collapses them to their rail, the way the right panel of the exercise details snaps shut. */
    protected onResizeEnd(event: { sizes: (number | string)[] }): void {
        const sizes = event.sizes.map(Number);
        if (this.primaryVertical() || sizes[1] > INSTRUCTIONS_SNAP_PERCENT) {
            this.chosenSizes.set(sizes);
            return;
        }
        // The sliver is not what the instructions reopen with. The splitter has stored it already, so the earlier split overwrites it.
        const before = this.sizesBeforeDrag ?? this.chosenSizes() ?? [65, 35];
        this.chosenSizes.set(before);
        try {
            localStorage.setItem(this.storageKey(), JSON.stringify(before));
        } catch {
            // Storage is blocked; the split still applies for this visit
        }
        this.instructionsCollapsed.set(true);
    }

    ngAfterViewInit(): void {
        this.resizeObserver = new ResizeObserver(([entry]) => this.width.set(entry.contentRect.width));
        this.resizeObserver.observe(this.elementRef.nativeElement);
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
    }
}
