import { TumAetUiSplitterComponent } from '@tumaet/ui-angular';
import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { parseJson } from 'app/foundation/util/json.util';
import { CollapsedPanelRailComponent, CollapsedPanelRailItem } from 'app/shared-ui/components/collapsed-panel-rail/collapsed-panel-rail.component';
import { FeedbackBottomPanelComponent } from 'app/exercise/feedback-bottom-panel/feedback-bottom-panel.component';
import { FeedbackSplitComponent } from 'app/exercise/feedback-bottom-panel/feedback-split.component';

const STACK_PRIMARY_AT = 840;
/** The smallest share (percent) the area of the canvas and the details keeps, and the smallest the instructions may be dragged to. */
const AREA_MIN_PERCENT = 36;
const INSTRUCTIONS_MIN_PERCENT = 8;
/** The share (percent) of the instructions at or below which dragging them narrower collapses them to their rail. */
const INSTRUCTIONS_SNAP_PERCENT = 15;
/**
 * Stacked, the instructions are sized along the height, and a panel is only limited in height: it collapses to its rail only when it is sized
 * along the width, as the right panel of the exercise details does. This is the smallest share (percent) they may be dragged to then.
 */
const HEIGHT_MIN_PERCENT = 24;
/** What the rail of collapsed instructions takes beside the area, and under it while they are stacked. */
const RAIL_WIDTH = '3rem';
const RAIL_HEIGHT = '2.75rem';

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
        TumAetUiSplitterComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        CollapsedPanelRailComponent,
        FeedbackBottomPanelComponent,
        FeedbackSplitComponent,
    ],
})
export class AssessmentWorkspaceComponent implements OnInit, AfterViewInit, OnDestroy {
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
    /**
     * The split the user last chose between the area of the canvas and the instructions, which collapsed instructions open with again. The
     * splitter keeps a drag until this input changes, so setting it to a new array puts the splitter back at this split.
     */
    private readonly chosenSizes = signal<number[] | undefined>(undefined);
    protected readonly detailsExpanded = signal(true);
    protected readonly instructionsCollapsed = signal(false);
    protected readonly primarySizes = computed(() => this.chosenSizes() ?? (this.primaryVertical() ? [60, 40] : [65, 35]));
    protected readonly primaryMinSizes = computed(() => [AREA_MIN_PERCENT, this.primaryVertical() ? HEIGHT_MIN_PERCENT : INSTRUCTIONS_MIN_PERCENT]);
    protected readonly railSize = computed(() => (this.instructionsCollapsed() ? (this.primaryVertical() ? RAIL_HEIGHT : RAIL_WIDTH) : undefined));
    protected readonly railOrientation = computed(() => (this.primaryVertical() ? 'horizontal' : 'vertical'));
    protected readonly faInstructions = faListAlt;
    protected readonly faChevronRight = faChevronRight;

    ngOnInit(): void {
        this.chosenSizes.set(this.readSavedSizes());
    }

    /** Dragging the instructions down to a sliver collapses them to their rail, the way the right panel of the exercise details snaps shut. */
    protected onResizeEnd(sizes: number[]): void {
        if (this.primaryVertical() || sizes[1] > INSTRUCTIONS_SNAP_PERCENT) {
            this.chosenSizes.set(sizes);
            this.saveSizes(sizes);
            return;
        }
        // The sliver is not what the instructions reopen with, so the splitter goes back to the split chosen before
        this.chosenSizes.set([...this.primarySizes()]);
        this.instructionsCollapsed.set(true);
    }

    private readSavedSizes(): number[] | undefined {
        try {
            const raw = localStorage.getItem(this.storageKey());
            const parsed = raw === null ? undefined : parseJson<unknown>(raw);
            if (Array.isArray(parsed) && parsed.length === 2 && parsed.every((size) => typeof size === 'number' && size > 0)) {
                return parsed;
            }
        } catch {
            // Not JSON, or storage is blocked: the default split applies
        }
        return undefined;
    }

    private saveSizes(sizes: number[]): void {
        try {
            localStorage.setItem(this.storageKey(), JSON.stringify(sizes));
        } catch {
            // Storage is blocked; the split still applies for this visit
        }
    }

    ngAfterViewInit(): void {
        this.resizeObserver = new ResizeObserver(([entry]) => this.width.set(entry.contentRect.width));
        this.resizeObserver.observe(this.elementRef.nativeElement);
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
    }
}
