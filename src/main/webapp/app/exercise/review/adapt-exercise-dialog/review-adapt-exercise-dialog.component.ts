import {
    TumUiButtonComponent,
    TumUiButtonDirective,
    TumUiCheckboxComponent,
    TumUiInputDirective,
    TumUiSearchFieldComponent,
    TumUiSelectButtonComponent,
    TumUiTagComponent,
} from '@tumaet/ui-angular';
import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AdaptFinding, reviewRepositoryLabel } from 'app/exercise/review/review-comment-utils';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { ConsistencyIssueSeverityEnum } from 'app/openapi/model/consistency-issue';

export interface ReviewAdaptExerciseDialogResult {
    instructions?: string;
    selectedFeedbackThreadIds?: number[];
}

/** Which findings the list shows; the selection itself is never filtered. */
export type AdaptFindingFilter = 'all' | 'findings' | 'comments' | 'selected';

interface AdaptFindingRow {
    finding: AdaptFinding;
    /** Row identity for tracking and ids; findings without a thread cannot be selected, and their key is positional. */
    key: string;
    selected: boolean;
    /** Whether the description is long enough that it is clamped until the instructor expands it. */
    clampable: boolean;
    expanded: boolean;
}

interface AdaptFindingGroup {
    targetType: CommentThreadLocationType | undefined;
    label: string;
    rows: AdaptFindingRow[];
    /** Selection state over the group's *selectable* rows, for the group checkbox. */
    selectedCount: number;
    selectableCount: number;
}

interface FilterOption {
    value: AdaptFindingFilter;
    labelKey: string;
    count: number;
}

/** Severity order so the most important findings surface first when there are many to triage. */
const SEVERITY_ORDER: Record<string, number> = {
    [ConsistencyIssueSeverityEnum.High]: 0,
    [ConsistencyIssueSeverityEnum.Medium]: 1,
    [ConsistencyIssueSeverityEnum.Low]: 2,
};

/** Group order follows how an instructor reads an exercise: the statement, then what students get, then the answer, then the tests. */
const GROUP_ORDER: (CommentThreadLocationType | undefined)[] = [
    CommentThreadLocationType.PROBLEM_STATEMENT,
    CommentThreadLocationType.TEMPLATE_REPO,
    CommentThreadLocationType.SOLUTION_REPO,
    CommentThreadLocationType.TEST_REPO,
    undefined,
];

const MAX_INSTRUCTIONS_LENGTH = 8000;
/** Above this many findings the list gains a filter and a search field; below it, scanning is quicker than filtering. */
const TRIAGE_TOOLS_THRESHOLD = 4;
/** Descriptions longer than this are clamped to a few lines until expanded, so one verbose finding cannot push the rest out of view. */
const CLAMP_DESCRIPTION_CHARS = 220;

/**
 * Body of the "adapt exercise" dialog: the review comments to address, grouped by where they sit in the exercise, and
 * the free-text instructions. Every comment is selectable here, so the dialog is a complete place to decide what the
 * adaptation acts on; with many comments it adds a filter, a search, and per-group selection. The host owns
 * presentation and the result — this component only reports the decision.
 */
@Component({
    selector: 'jhi-review-adapt-exercise-dialog',
    templateUrl: './review-adapt-exercise-dialog.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        RouterLink,
        TumUiButtonDirective,
        FormsModule,
        TumUiButtonComponent,
        TumUiTagComponent,
        TumUiInputDirective,
        TumUiCheckboxComponent,
        TumUiSearchFieldComponent,
        TumUiSelectButtonComponent,
        FaIconComponent,
        ArtemisTranslatePipe,
        TranslateDirective,
    ],
})
export class ReviewAdaptExerciseDialogComponent {
    private readonly translateService = inject(TranslateService);

    readonly findings = input<AdaptFinding[]>([]);
    readonly selectedFeedbackThreadIds = input<number[]>();
    readonly progressLink = input<(string | number)[]>();
    readonly blockedReason = input<string>();
    readonly submitting = input(false);
    readonly submissionError = input<string>();

    readonly confirmed = output<ReviewAdaptExerciseDialogResult>();
    readonly cancelled = output<void>();

    readonly instructions = signal('');
    readonly selectedIds = linkedSignal(() => this.selectedFeedbackThreadIds() ?? []);
    protected readonly filter = signal<AdaptFindingFilter>('all');
    protected readonly query = signal('');
    private readonly expandedKeys = signal<ReadonlySet<string>>(new Set());

    protected readonly facArtemisIntelligence = facArtemisIntelligence;
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;
    protected readonly maxInstructionsLength = MAX_INSTRUCTIONS_LENGTH;

    private readonly languageChange = toSignal(this.translateService.onLangChange, { initialValue: undefined });

    /** Every finding as a row, in triage order: severity first, then the line it sits on. Selection state is read from the whole list, never the filtered one. */
    private readonly rows = computed<AdaptFindingRow[]>(() => {
        const selectAll = this.selectedFeedbackThreadIds() === undefined;
        const selectedIds = this.selectedIds();
        const expanded = this.expandedKeys();
        return [...this.findings()]
            .sort((a, b) => (a.severity ? SEVERITY_ORDER[a.severity] : 3) - (b.severity ? SEVERITY_ORDER[b.severity] : 3) || (a.lineNumber ?? 0) - (b.lineNumber ?? 0))
            .map((finding, index) => {
                const key = finding.threadId !== undefined ? `thread-${finding.threadId}` : `finding-${index}`;
                return {
                    finding,
                    key,
                    selected: selectAll || (finding.threadId !== undefined && selectedIds.includes(finding.threadId)),
                    clampable: finding.description.length > CLAMP_DESCRIPTION_CHARS || finding.description.split('\n').length > 3,
                    expanded: expanded.has(key),
                };
            });
    });

    protected readonly totalCount = computed(() => this.rows().length);
    protected readonly selectedCount = computed(() => this.rows().filter((row) => row.selected).length);
    protected readonly showTriageTools = computed(() => this.totalCount() > TRIAGE_TOOLS_THRESHOLD);

    protected readonly filterOptions = computed<FilterOption[]>(() => {
        const rows = this.rows();
        return [
            { value: 'all', labelKey: 'artemisApp.review.adaptExercise.filter.all', count: rows.length },
            { value: 'findings', labelKey: 'artemisApp.review.adaptExercise.filter.findings', count: rows.filter((row) => row.finding.source === 'finding').length },
            { value: 'comments', labelKey: 'artemisApp.review.adaptExercise.filter.comments', count: rows.filter((row) => row.finding.source === 'comment').length },
            { value: 'selected', labelKey: 'artemisApp.review.adaptExercise.filter.selected', count: this.selectedCount() },
        ];
    });

    /** The rows the list shows after the filter and the search; grouped by where they sit in the exercise. */
    protected readonly groups = computed<AdaptFindingGroup[]>(() => {
        this.languageChange();
        const filter = this.filter();
        const query = this.query().trim().toLocaleLowerCase();
        const visible = this.rows().filter((row) => matchesFilter(row, filter) && matchesQuery(row.finding, query));
        return GROUP_ORDER.map((targetType) => {
            const rows = visible.filter((row) => row.finding.targetType === targetType);
            const selectable = rows.filter((row) => row.finding.threadId !== undefined);
            return {
                targetType,
                label: targetType
                    ? reviewRepositoryLabel(targetType, this.translateService)
                    : this.translateService.instant('artemisApp.review.relatedLocationRepository.repository'),
                rows,
                selectedCount: selectable.filter((row) => row.selected).length,
                selectableCount: selectable.length,
            };
        }).filter((group) => group.rows.length > 0);
    });
    protected readonly visibleCount = computed(() => this.groups().reduce((count, group) => count + group.rows.length, 0));

    protected readonly isFreeMode = computed(() => this.selectedCount() === 0);
    protected readonly remainingCharacters = computed(() => MAX_INSTRUCTIONS_LENGTH - this.instructions().length);
    /** Without findings there is nothing to act on, so free-form instructions become mandatory. */
    protected readonly confirmDisabled = computed(
        () => !!this.blockedReason() || this.submitting() || this.instructions().length > MAX_INSTRUCTIONS_LENGTH || (this.isFreeMode() && this.instructions().trim().length === 0),
    );

    protected toggleFinding(threadId: number, selected: boolean): void {
        this.selectedIds.update((ids) => (selected ? (ids.includes(threadId) ? ids : [...ids, threadId]) : ids.filter((id) => id !== threadId)));
    }

    /** Selects or clears every selectable finding of a group at once; the group checkbox shows a dash while a group is partly selected. */
    protected toggleGroup(group: AdaptFindingGroup, selected: boolean): void {
        const threadIds = group.rows.map((row) => row.finding.threadId).filter((id): id is number => id !== undefined);
        this.selectedIds.update((ids) => (selected ? [...ids, ...threadIds.filter((id) => !ids.includes(id))] : ids.filter((id) => !threadIds.includes(id))));
    }

    /** Selects what the list currently shows, so "select all" under a filter or a search means exactly what is on screen. */
    protected selectVisible(): void {
        for (const group of this.groups()) {
            this.toggleGroup(group, true);
        }
    }

    protected clearSelection(): void {
        this.selectedIds.set([]);
    }

    protected toggleExpanded(key: string): void {
        this.expandedKeys.update((keys) => {
            const next = new Set(keys);
            if (!next.delete(key)) {
                next.add(key);
            }
            return next;
        });
    }

    protected onFilterChange(value: unknown): void {
        this.filter.set(isFilter(value) ? value : 'all');
    }

    protected confirm(): void {
        if (this.confirmDisabled()) {
            return;
        }
        const result: ReviewAdaptExerciseDialogResult = { instructions: this.instructions().trim() || undefined };
        if (this.selectedFeedbackThreadIds() !== undefined) {
            result.selectedFeedbackThreadIds = this.rows()
                .filter((row) => row.selected)
                .map((row) => row.finding.threadId!)
                .filter((id) => id !== undefined);
        }
        this.confirmed.emit(result);
    }
}

function isFilter(value: unknown): value is AdaptFindingFilter {
    return value === 'all' || value === 'findings' || value === 'comments' || value === 'selected';
}

function matchesFilter(row: AdaptFindingRow, filter: AdaptFindingFilter): boolean {
    switch (filter) {
        case 'findings':
            return row.finding.source === 'finding';
        case 'comments':
            return row.finding.source === 'comment';
        case 'selected':
            return row.selected;
        default:
            return true;
    }
}

function matchesQuery(finding: AdaptFinding, query: string): boolean {
    if (!query) {
        return true;
    }
    return [finding.description, finding.locationLabel, finding.category, finding.authorName].some((text) => text?.toLocaleLowerCase().includes(query));
}
