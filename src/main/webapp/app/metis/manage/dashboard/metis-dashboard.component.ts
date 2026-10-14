import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft, faChevronDown, faChevronRight, faCog, faComments, faFileExport, faListCheck, faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import {
    TumAetUiButtonDirective,
    TumAetUiSearchFieldComponent,
    TumAetUiSelectComponent,
    TumAetUiTableDirective,
    TumAetUiTableSortEvent,
    TumAetUiTableSortableColumnComponent,
    TumAetUiTagComponent,
    TumAetUiTagSeverity,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import dayjs from 'dayjs/esm';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { downloadFile } from 'app/foundation/util/download.util';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { RatingDistributionComponent } from 'app/metis/manage/dashboard/rating-distribution/rating-distribution.component';
import { METIS_DEFAULT_CONFIG, MetisCheckStatus, MetisDashboardRowDTO, MetisDashboardSummaryDTO, MetisQuestionDTO } from 'app/metis/shared/entities/metis.model';
import { FocusChipComponent } from 'app/metis/shared/focus-chip/focus-chip.component';
import { RatingSquareComponent } from 'app/metis/shared/rating-square/rating-square.component';
import { MetisService } from 'app/metis/shared/services/metis.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

/** The filter value that keeps every row. */
const ALL = 'ALL';

/** The status groups the status filter offers; "not started" covers every state before the student opened the check. */
export type StatusFilter = 'ALL' | 'COMPLETED' | 'IN_PROGRESS' | 'NOT_STARTED' | 'SKIPPED' | 'FAILED';

/** The table columns that can be sorted. */
export type SortField = 'studentName' | 'tutorialGroupTitle' | 'status' | 'followUps' | 'average' | 'completedAt';

/** The sort orders the sort select offers; each one is a column and a direction. */
export type SortPreset = 'name' | 'lowestAverage' | 'mostFollowUps' | 'completedLast';

interface SelectOption<T> {
    label: string;
    value: T;
}

/** One table row: the DTO plus the figures the columns show, computed once per render instead of once per cell. */
export interface DashboardRowView {
    row: MetisDashboardRowDTO;
    /** The questions generated up front; follow-ups hang below them and are only counted. */
    mainQuestions: MetisQuestionDTO[];
    followUps: number;
    /** The mean first-answer rating of the main questions, or undefined while none is rated. */
    average?: number;
    completedAt?: dayjs.Dayjs;
    expanded: boolean;
}

const STATUS_SEVERITY: Record<MetisCheckStatus, TumAetUiTagSeverity> = {
    [MetisCheckStatus.COMPLETED]: 'success',
    [MetisCheckStatus.IN_PROGRESS]: 'info',
    [MetisCheckStatus.READY]: 'secondary',
    [MetisCheckStatus.PENDING]: 'secondary',
    [MetisCheckStatus.GENERATING]: 'secondary',
    [MetisCheckStatus.IN_REVIEW]: 'secondary',
    [MetisCheckStatus.SKIPPED]: 'secondary',
    [MetisCheckStatus.FAILED]: 'danger',
};

const STATUS_FILTERS: Record<Exclude<StatusFilter, 'ALL'>, MetisCheckStatus[]> = {
    COMPLETED: [MetisCheckStatus.COMPLETED],
    IN_PROGRESS: [MetisCheckStatus.IN_PROGRESS],
    NOT_STARTED: [MetisCheckStatus.PENDING, MetisCheckStatus.GENERATING, MetisCheckStatus.IN_REVIEW, MetisCheckStatus.READY],
    SKIPPED: [MetisCheckStatus.SKIPPED],
    FAILED: [MetisCheckStatus.FAILED],
};

const STATUS_FILTER_OPTIONS: { value: StatusFilter; key: string }[] = [
    { value: 'ALL', key: 'allStatuses' },
    { value: 'COMPLETED', key: 'completed' },
    { value: 'IN_PROGRESS', key: 'inProgress' },
    { value: 'NOT_STARTED', key: 'notStarted' },
    { value: 'SKIPPED', key: 'skipped' },
    { value: 'FAILED', key: 'failed' },
];

const SORT_PRESETS: Record<SortPreset, { field: SortField; order: number; key: string }> = {
    name: { field: 'studentName', order: 1, key: 'sortName' },
    lowestAverage: { field: 'average', order: 1, key: 'sortLowestAverage' },
    mostFollowUps: { field: 'followUps', order: -1, key: 'sortMostFollowUps' },
    completedLast: { field: 'completedAt', order: -1, key: 'sortCompletedLast' },
};

const SORT_PRESET_KEYS: SortPreset[] = ['name', 'lowestAverage', 'mostFollowUps', 'completedLast'];

const SORT_FIELDS: SortField[] = ['studentName', 'tutorialGroupTitle', 'status', 'followUps', 'average', 'completedAt'];

const SORT_VALUE: Record<SortField, (view: DashboardRowView) => string | number | undefined> = {
    studentName: (view) => view.row.studentName,
    tutorialGroupTitle: (view) => view.row.tutorialGroupTitle,
    status: (view) => view.row.check.status,
    followUps: (view) => view.followUps,
    average: (view) => view.average,
    completedAt: (view) => view.completedAt?.valueOf(),
};

function isSortField(field: string): field is SortField {
    return SORT_FIELDS.some((candidate) => candidate === field);
}

function toRowView(row: MetisDashboardRowDTO, expanded: boolean): DashboardRowView {
    const mainQuestions = row.questions.filter((question) => question.parentQuestionId === undefined);
    const scores = mainQuestions.map((question) => question.llmScore).filter((score): score is number => score !== undefined);
    return {
        row,
        mainQuestions,
        followUps: row.questions.length - mainQuestions.length,
        average: scores.length ? scores.reduce((sum, score) => sum + score, 0) / scores.length : undefined,
        completedAt: row.check.completedAt,
        expanded,
    };
}

/** Rows without a value for the column sink to the bottom whichever way the column sorts; ties fall back to the name. */
function compareRowViews(a: DashboardRowView, b: DashboardRowView, field: SortField, order: number): number {
    const valueA = SORT_VALUE[field](a);
    const valueB = SORT_VALUE[field](b);
    const byName = a.row.studentName.localeCompare(b.row.studentName);
    if (valueA === undefined || valueB === undefined) {
        if (valueA === valueB) {
            return byName;
        }
        return valueA === undefined ? 1 : -1;
    }
    const result = typeof valueA === 'string' && typeof valueB === 'string' ? valueA.localeCompare(valueB) : Number(valueA) - Number(valueB);
    return result !== 0 ? result * order : byName;
}

/**
 * The instructor's view of a comprehension check: how far the cohort is, how the ratings are distributed, and one
 * row per participation that opens into the student's question thread.
 *
 * The page reads the exercise the route resolved and everything else through {@link MetisService}. Instructors can
 * also have the missing checks generated at once, for an exercise whose due date the scheduler no longer sweeps.
 */
@Component({
    selector: 'jhi-metis-dashboard',
    templateUrl: './metis-dashboard.component.html',
    styleUrl: './metis-dashboard.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        DecimalPipe,
        FormsModule,
        RouterLink,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        CourseTitleBarTitleDirective,
        CourseTitleBarActionsDirective,
        CourseTitleBarTitleComponent,
        TumAetUiButtonDirective,
        TumAetUiTooltipDirective,
        TumAetUiTagComponent,
        TumAetUiTableDirective,
        TumAetUiTableSortableColumnComponent,
        TumAetUiSelectComponent,
        TumAetUiSearchFieldComponent,
        RatingDistributionComponent,
        RatingSquareComponent,
        FocusChipComponent,
    ],
})
export class MetisDashboardComponent implements OnInit {
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly metisService = inject(MetisService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    /** Resolved by the route; the page is only reachable with it. */
    protected readonly exercise: ProgrammingExercise = this.activatedRoute.snapshot.data['exercise'];
    protected readonly courseId = Number(this.activatedRoute.parent?.snapshot.paramMap.get('courseId'));

    /** The points of the exercise's Likert scale, from its configuration; every rating is read against it. */
    protected readonly points = signal(METIS_DEFAULT_CONFIG.likertScalePoints);

    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronRight = faChevronRight;
    protected readonly faCog = faCog;
    protected readonly faComments = faComments;
    protected readonly faFileExport = faFileExport;
    protected readonly faListCheck = faListCheck;
    protected readonly faWandMagicSparkles = faWandMagicSparkles;

    protected readonly summary = signal<MetisDashboardSummaryDTO | undefined>(undefined);
    readonly rows = signal<MetisDashboardRowDTO[]>([]);
    /** Participations whose question thread is open. Everything starts collapsed. */
    readonly expandedIds = signal<ReadonlySet<number>>(new Set());

    readonly groupFilter = signal<string>(ALL);
    readonly statusFilter = signal<StatusFilter>(ALL);
    readonly sortField = signal<SortField>('studentName');
    readonly sortOrder = signal(1);
    readonly searchTerm = signal('');

    private readonly locale = getCurrentLocaleSignal(this.translateService);

    protected readonly groupOptions = computed<SelectOption<string>[]>(() => {
        this.locale();
        const groups = [
            ...new Set(
                this.rows()
                    .map((row) => row.tutorialGroupTitle)
                    .filter((title): title is string => !!title),
            ),
        ].sort((a, b) => a.localeCompare(b));
        return [{ label: this.translateService.instant('artemisApp.metis.dashboard.filters.allGroups'), value: ALL }, ...groups.map((group) => ({ label: group, value: group }))];
    });

    protected readonly statusOptions = computed<SelectOption<StatusFilter>[]>(() => {
        this.locale();
        return STATUS_FILTER_OPTIONS.map((option) => ({ label: this.translateService.instant(`artemisApp.metis.dashboard.filters.${option.key}`), value: option.value }));
    });

    protected readonly sortOptions = computed<SelectOption<SortPreset>[]>(() => {
        this.locale();
        return SORT_PRESET_KEYS.map((preset) => ({ label: this.translateService.instant(`artemisApp.metis.dashboard.filters.${SORT_PRESETS[preset].key}`), value: preset }));
    });

    /** The preset the current column sort corresponds to, or undefined after a header click that matches none. */
    protected readonly sortPreset = computed<SortPreset | undefined>(() =>
        SORT_PRESET_KEYS.find((preset) => SORT_PRESETS[preset].field === this.sortField() && SORT_PRESETS[preset].order === this.sortOrder()),
    );

    private readonly rowViews = computed<DashboardRowView[]>(() => {
        const expanded = this.expandedIds();
        return this.rows().map((row) => toRowView(row, expanded.has(row.participationId)));
    });

    readonly visibleRows = computed<DashboardRowView[]>(() => {
        const group = this.groupFilter();
        const statusFilter = this.statusFilter();
        const statuses = statusFilter === ALL ? undefined : STATUS_FILTERS[statusFilter];
        const term = this.searchTerm().trim().toLowerCase();
        const field = this.sortField();
        const order = this.sortOrder();
        return this.rowViews()
            .filter((view) => group === ALL || view.row.tutorialGroupTitle === group)
            .filter((view) => !statuses || statuses.includes(view.row.check.status))
            .filter((view) => !term || view.row.studentName.toLowerCase().includes(term) || view.row.studentLogin.toLowerCase().includes(term))
            .sort((a, b) => compareRowViews(a, b, field, order));
    });

    protected readonly totalCount = computed(() => this.rows().length);

    /** How many main questions a student gets, for the ratings column header. */
    protected readonly questionCount = computed(() => Math.max(0, ...this.rowViews().map((view) => view.mainQuestions.length)));

    ngOnInit(): void {
        this.load();
    }

    private load(): void {
        this.metisService
            .config(this.exercise.id!)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((config) => this.points.set(config.likertScalePoints));
        this.metisService
            .dashboard(this.exercise.id!)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((dashboard) => {
                this.summary.set(dashboard.summary);
                this.rows.set([...dashboard.rows]);
            });
    }

    toggleExpanded(participationId: number): void {
        this.expandedIds.update((ids) => {
            const next = new Set(ids);
            if (!next.delete(participationId)) {
                next.add(participationId);
            }
            return next;
        });
    }

    onGroupFilterChange(group: string): void {
        this.groupFilter.set(group);
    }

    onStatusFilterChange(status: StatusFilter): void {
        this.statusFilter.set(status);
    }

    onSearchTermChange(term: string): void {
        this.searchTerm.set(term);
    }

    onSortPresetChange(preset: SortPreset | undefined): void {
        if (!preset) {
            return;
        }
        this.sortField.set(SORT_PRESETS[preset].field);
        this.sortOrder.set(SORT_PRESETS[preset].order);
    }

    /** A header click sorts by that column; a second click on the same header turns the order around. */
    onSortChange(event: TumAetUiTableSortEvent): void {
        if (!isSortField(event.field)) {
            return;
        }
        this.sortField.set(event.field);
        this.sortOrder.set(event.order);
    }

    statusSeverity(status: MetisCheckStatus): TumAetUiTagSeverity {
        return STATUS_SEVERITY[status];
    }

    /** Downloads the server's CSV export: one row per question of every check, with the answers and ratings. */
    exportCsv(): void {
        this.metisService
            .exportCsv(this.exercise.id!)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (blob) => {
                    const fileName = `${this.exercise.shortName ?? this.exercise.title}-comprehension-check`;
                    this.downloadBlob(blob, fileName);
                    this.alertService.success('artemisApp.metis.dashboard.exported', { fileName, count: this.totalCount() });
                },
                error: (error: unknown) => {
                    // The global HTTP error handling reports a refused request; a response without a file needs a message of its own.
                    if (!(error instanceof HttpErrorResponse)) {
                        this.alertService.error('artemisApp.metis.dashboard.exportFailed');
                    }
                },
            });
    }

    /**
     * Creates the missing checks of the exercise and queues the failed ones again; the questions appear within a few
     * minutes, so the page reloads to show the new rows. Only instructors see the button.
     */
    generateNow(): void {
        this.metisService
            .generateNow(this.exercise.id!)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((result) => {
                this.alertService.success('artemisApp.metis.dashboard.generated', { created: result.createdChecks, requeued: result.requeuedChecks });
                this.load();
            });
    }

    /** Hands the file to the browser's download. A method of its own so a spec can observe the call without reaching into the util module. */
    protected downloadBlob(blob: Blob, fileName: string): void {
        downloadFile(blob, `${fileName}.csv`);
    }
}
