import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of, switchMap, timer } from 'rxjs';
import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { TumUiTableDirective } from 'app/shared-ui/tum-ui/table-directive/tum-ui-table.directive';
import { TumUiPanelComponent } from 'app/shared-ui/tum-ui/panel/tum-ui-panel.component';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { TumUiMessageComponent } from 'app/shared-ui/tum-ui/message/tum-ui-message.component';
import { TumUiSelectComponent } from 'app/shared-ui/tum-ui/select/tum-ui-select.component';
import { TumUiInputDirective } from 'app/shared-ui/tum-ui/input/tum-ui-input.directive';
import { TumUiPaginatorComponent } from 'app/shared-ui/tum-ui/paginator/tum-ui-paginator.component';
import { TumUiDialogComponent } from 'app/shared-ui/tum-ui/dialog/tum-ui-dialog.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Router } from '@angular/router';
import {
    faAlignLeft,
    faBook,
    faBookOpen,
    faCheck,
    faChevronRight,
    faCircle,
    faCircleCheck,
    faCircleQuestion,
    faCommentDots,
    faComments,
    faDatabase,
    faFileLines,
    faGraduationCap,
    faHashtag,
    faImage,
    faLayerGroup,
    faListCheck,
    faSpinner,
    faSync,
    faTriangleExclamation,
    faUpRightFromSquare,
    faWaveSquare,
    faXmark,
} from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { CourseIngestionDashboardService } from './course-ingestion-dashboard.service';
import {
    ActiveIngestion,
    ContentCensus,
    CourseIndexCensus,
    DisplayStep,
    IndexOverview,
    IndexedContent,
    IndexedContentObject,
    IndexedEntity,
    IngestionActivity,
    MissingContent,
    MissingEntity,
    PyrisReachability,
    RecentIngestion,
    TypeIndexCensus,
} from './course-ingestion-dashboard.model';

/** No status callback for this long (ms) marks a run as possibly stalled - fast enough to catch a killed Iris. */
const STALL_THRESHOLD_MS = 90000;

/** How often the active-ingestions section polls the backend for live progress. */
const ACTIVE_POLL_INTERVAL_MS = 3000;

type CellStatus = 'incomplete' | 'unknown' | 'ok' | 'na';
type ScopeFilter = 'all' | 'active' | 'archived';
type StatusFilter = 'all' | 'incomplete' | 'complete' | 'unknown';

interface SelectOption<T> {
    label: string;
    value: T;
}

/** Fixed column order for the completeness matrix. Column labels are resolved via i18n by type key. */
const MATRIX_TYPES: string[] = ['exercise', 'lecture', 'lecture_unit', 'exam', 'faq', 'channel', 'course', 'post', 'answer_post'];

/** Fixed column order for the Iris lecture-content columns (pdf, video). Shown only when Iris is enabled. */
const CONTENT_TYPES: string[] = ['pdf', 'video'];

/** Worst-first ordering of cell statuses. */
const STATUS_ORDER: CellStatus[] = ['incomplete', 'unknown', 'ok', 'na'];

const PAGE_SIZE_OPTIONS = [10, 25, 50, 100];

@Component({
    selector: 'jhi-course-ingestion-dashboard',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        DecimalPipe,
        DatePipe,
        NgTemplateOutlet,
        FormsModule,
        TumUiTableDirective,
        TumUiPanelComponent,
        TumUiButtonComponent,
        TumUiMessageComponent,
        TumUiSelectComponent,
        TumUiInputDirective,
        TumUiPaginatorComponent,
        TumUiDialogComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
    ],
    templateUrl: './course-ingestion-dashboard.component.html',
})
export class CourseIngestionDashboardComponent implements OnInit {
    private dashboardService = inject(CourseIngestionDashboardService);
    private destroyRef = inject(DestroyRef);
    private translateService = inject(TranslateService);
    private router = inject(Router);

    protected readonly faSync = faSync;
    protected readonly faDatabase = faDatabase;
    protected readonly faLayerGroup = faLayerGroup;
    protected readonly faXmark = faXmark;
    protected readonly faCheck = faCheck;
    protected readonly faSpinner = faSpinner;
    protected readonly faCircle = faCircle;
    protected readonly faChevronRight = faChevronRight;
    protected readonly faCircleCheck = faCircleCheck;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faBookOpen = faBookOpen;
    protected readonly faFileLines = faFileLines;
    protected readonly faUpRightFromSquare = faUpRightFromSquare;
    protected readonly types = MATRIX_TYPES;

    // Live active ingestions, polled every few seconds so the milestone view auto-updates. A ticking clock signal
    // drives the elapsed-time display between polls.
    readonly activeIngestions = signal<ActiveIngestion[]>([]);
    readonly recentIngestions = signal<RecentIngestion[]>([]);
    // Iris reachability, polled with the active ingestions; undefined until the first probe returns.
    readonly pyrisReachable = signal<boolean | undefined>(undefined);
    // Whether the Iris integration is configured; the Iris-only sections (active/recent ingestions, Iris health) are
    // hidden entirely on deployments without Iris.
    readonly irisEnabled = computed(() => this.overview()?.irisEnabled ?? false);
    private readonly nowMillis = signal(Date.now());

    // The run whose milestone detail drawer is open. Keyed by job id so the drawer content follows live poll updates.
    readonly selectedJobId = signal<string | undefined>(undefined);
    readonly selectedRun = computed(() => this.activeIngestions().find((run) => run.jobId === this.selectedJobId()));
    readonly selectedRecent = computed(() => this.recentIngestions().find((run) => run.jobId === this.selectedJobId()));
    protected readonly contentTypes = CONTENT_TYPES;
    protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;

    /** Iris content columns are shown only when the backend returned content, which it does only when Iris is enabled. */
    readonly showContent = computed(() => (this.census() ?? []).some((course) => course.content && course.content.length > 0));

    readonly overview = signal<IndexOverview | undefined>(undefined);
    readonly loading = signal(true);
    readonly error = signal(false);

    readonly census = signal<CourseIndexCensus[] | undefined>(undefined);
    readonly censusLoading = signal(true);
    readonly censusError = signal(false);

    // Toolbar state: free-text search, active/archived scope, and a completeness-status filter.
    readonly search = signal('');
    readonly scope = signal<ScopeFilter>('all');
    readonly statusFilter = signal<StatusFilter>('all');

    // Sort state: 'worst' is the default attention-first order; clicking a header sorts by that column and toggles direction.
    readonly sortColumn = signal<'worst' | 'course' | 'release'>('worst');
    readonly sortAscending = signal(true);

    // Pagination over the filtered/sorted courses (0-based page, matching tum-ui-paginator).
    readonly pageSize = signal(25);
    readonly currentPage = signal(0);

    // Course-content browser modal: the course whose stored Weaviate objects are shown, and the loaded objects.
    readonly browsedCourse = signal<CourseIndexCensus | undefined>(undefined);
    readonly indexedEntities = signal<IndexedEntity[]>([]);
    readonly indexedContent = signal<IndexedContent[]>([]);
    readonly indexedMissing = signal<MissingEntity[]>([]);
    readonly contentGaps = signal<MissingContent[]>([]);
    readonly entitiesLoading = signal(false);
    readonly entitiesError = signal(false);
    // Keys of objects whose full stored fields are expanded in the browser modal (entity or content object).
    readonly expandedEntities = signal<Set<string>>(new Set());
    // The tree node currently selected in the browser modal (drives the right detail pane), and the expanded tree nodes.
    readonly selectedKey = signal<string>('');
    readonly treeOpen = signal<Set<string>>(new Set());

    /** Translated options for the scope select, rebuilt on demand so they follow the active language. */
    protected scopeOptions(): SelectOption<ScopeFilter>[] {
        return [
            { label: this.translate('scope_all'), value: 'all' },
            { label: this.translate('scope_active'), value: 'active' },
            { label: this.translate('scope_archived'), value: 'archived' },
        ];
    }

    /** Translated options for the status filter select. */
    protected statusOptions(): SelectOption<StatusFilter>[] {
        return [
            { label: this.translate('status_all'), value: 'all' },
            { label: this.translate('status_incomplete'), value: 'incomplete' },
            { label: this.translate('status_complete'), value: 'complete' },
            { label: this.translate('status_unknown'), value: 'unknown' },
        ];
    }

    /** Courses after search, scope, and status filtering, sorted by the active column (worst-first by default). */
    readonly displayedCourses = computed(() => {
        let courses = [...(this.census() ?? [])];
        const query = this.search().trim().toLowerCase();
        if (query) {
            courses = courses.filter((course) => (course.courseTitle ?? '').toLowerCase().includes(query));
        }
        const scope = this.scope();
        if (scope === 'active') {
            courses = courses.filter((course) => course.active);
        } else if (scope === 'archived') {
            courses = courses.filter((course) => !course.active);
        }
        const status = this.statusFilter();
        if (status === 'incomplete') {
            courses = courses.filter((course) => this.courseWorst(course) === 'incomplete');
        } else if (status === 'complete') {
            courses = courses.filter((course) => this.courseWorst(course) === 'ok');
        } else if (status === 'unknown') {
            courses = courses.filter((course) => this.courseWorst(course) === 'unknown' || this.courseWorst(course) === 'na');
        }
        const direction = this.sortAscending() ? 1 : -1;
        const column = this.sortColumn();
        if (column === 'course') {
            courses.sort((first, second) => direction * (first.courseTitle ?? '').localeCompare(second.courseTitle ?? ''));
        } else if (column === 'release') {
            courses.sort((first, second) => direction * (first.startDate ?? '').localeCompare(second.startDate ?? ''));
        } else {
            courses.sort((first, second) => this.courseRank(first) - this.courseRank(second));
        }
        return courses;
    });

    /** The current page clamped into range, since filters can shrink the list below the current page index. */
    readonly clampedPage = computed(() => {
        const pages = Math.max(1, Math.ceil(this.displayedCourses().length / this.pageSize()));
        return Math.min(this.currentPage(), pages - 1);
    });

    /** The slice of courses shown on the current page. */
    readonly pagedCourses = computed(() => {
        const start = this.clampedPage() * this.pageSize();
        return this.displayedCourses().slice(start, start + this.pageSize());
    });

    /** Clicking a sortable header selects that column (ascending); clicking it again toggles the direction. */
    protected toggleSort(column: 'course' | 'release'): void {
        if (this.sortColumn() === column) {
            this.sortAscending.update((ascending) => !ascending);
        } else {
            this.sortColumn.set(column);
            this.sortAscending.set(true);
        }
    }

    /** The arrow shown in a sortable header: up/down when it is the active column, empty otherwise. */
    protected sortIndicator(column: 'course' | 'release'): string {
        if (this.sortColumn() !== column) {
            return '';
        }
        return this.sortAscending() ? '▲' : '▼';
    }

    protected onSearchChange(value: string): void {
        this.search.set(value);
        this.currentPage.set(0);
    }

    protected onScopeChange(value: ScopeFilter): void {
        this.scope.set(value);
        this.currentPage.set(0);
    }

    protected onStatusFilterChange(value: StatusFilter): void {
        this.statusFilter.set(value);
        this.currentPage.set(0);
    }

    protected onPageChange(page: number): void {
        this.currentPage.set(page);
    }

    protected onPageSizeChange(size: number): void {
        this.pageSize.set(size);
        this.currentPage.set(0);
    }

    /** True when any filter or a non-default sort is active, so the Reset control is offered. */
    readonly hasActiveFilters = computed(() => this.search() !== '' || this.scope() !== 'all' || this.statusFilter() !== 'all' || this.sortColumn() !== 'worst');

    /** Clears search, scope, and status filters and returns the table to the default worst-first sort. */
    protected resetFilters(): void {
        this.search.set('');
        this.scope.set('all');
        this.statusFilter.set('all');
        this.sortColumn.set('worst');
        this.sortAscending.set(true);
        this.currentPage.set(0);
    }

    ngOnInit(): void {
        this.refresh();
        this.pollActiveIngestions();
        // A 1-second clock so elapsed times count up visibly between the 3-second data polls.
        timer(1000, 1000)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
                if (this.activeIngestions().length > 0) {
                    this.nowMillis.set(Date.now());
                }
            });
    }

    /** Polls the active and recent ingestion endpoints on an interval so the views update live. Errors yield empty lists. */
    private pollActiveIngestions(): void {
        timer(0, ACTIVE_POLL_INTERVAL_MS)
            .pipe(
                switchMap(() => {
                    // On deployments without Iris there are no ingestion or Iris-health endpoints, so skip the polling
                    // entirely rather than hitting endpoints that do not exist.
                    if (!this.irisEnabled()) {
                        return of<{ active: ActiveIngestion[]; recent: RecentIngestion[]; health: PyrisReachability }>({ active: [], recent: [], health: { reachable: false } });
                    }
                    return forkJoin({
                        active: this.dashboardService.getActiveIngestions().pipe(catchError(() => of([] as ActiveIngestion[]))),
                        recent: this.dashboardService.getRecentIngestions().pipe(catchError(() => of([] as RecentIngestion[]))),
                        health: this.dashboardService.getPyrisHealth().pipe(catchError(() => of<PyrisReachability>({ reachable: false }))),
                    });
                }),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe(({ active, recent, health }) => {
                this.activeIngestions.set(active);
                this.recentIngestions.set(recent);
                this.pyrisReachable.set(health.reachable);
                this.nowMillis.set(Date.now());
            });
    }

    refresh(): void {
        this.loadOverview();
        this.loadCensus();
    }

    private loadOverview(): void {
        this.loading.set(true);
        this.error.set(false);
        this.dashboardService
            .getOverview()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (data) => {
                    this.overview.set(data);
                    this.loading.set(false);
                },
                error: () => {
                    this.loading.set(false);
                    this.error.set(true);
                },
            });
    }

    private loadCensus(): void {
        this.censusLoading.set(true);
        this.censusError.set(false);
        this.dashboardService
            .getIndexCensus()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (data) => {
                    this.census.set(data);
                    this.censusLoading.set(false);
                },
                error: () => {
                    this.censusLoading.set(false);
                    this.censusError.set(true);
                },
            });
    }

    protected typeCensus(course: CourseIndexCensus, typeKey: string): TypeIndexCensus | undefined {
        return course.types.find((entry) => entry.type === typeKey);
    }

    protected cellStatus(entry: TypeIndexCensus | undefined): CellStatus {
        if (!entry || (entry.expected === null && entry.present === 0)) {
            return 'na';
        }
        if (entry.expected === null) {
            return 'unknown';
        }
        return (entry.missing ?? 0) === 0 && (entry.orphaned ?? 0) === 0 ? 'ok' : 'incomplete';
    }

    protected cellText(entry: TypeIndexCensus | undefined): string {
        if (!entry || (entry.expected === null && entry.present === 0)) {
            return '–';
        }
        if (entry.expected === null) {
            return `${entry.present}`;
        }
        return `${entry.present}/${entry.expected}`;
    }

    /**
     * The course-level status. It is driven by the types we can actually measure (those with an expected count):
     * a single measurable type that is missing/orphaned makes the course Incomplete; otherwise, if any measurable
     * type is complete, the course is Complete. Only when a course has no measurable type at all - just present-only
     * types whose expected count we cannot compute yet - does it fall back to Unknown, so present-only types no longer
     * mask the completeness of the measured ones.
     */
    protected courseWorst(course: CourseIndexCensus): CellStatus {
        const statuses = [
            ...this.types.map((type) => this.cellStatus(this.typeCensus(course, type))),
            ...this.contentTypes.map((contentType) => this.contentCellStatus(this.contentCensus(course, contentType))),
        ];
        if (statuses.includes('incomplete')) {
            return 'incomplete';
        }
        if (statuses.includes('ok')) {
            return 'ok';
        }
        if (statuses.includes('unknown')) {
            return 'unknown';
        }
        return 'na';
    }

    protected statusLabelKey(status: CellStatus): string {
        const suffix = { incomplete: 'incomplete', unknown: 'unknown', ok: 'complete', na: 'empty' }[status];
        return `artemisApp.courseIngestionDashboard.matrix.status_${suffix}`;
    }

    /**
     * The inline left-border style that visually splits the metadata / content column groups. It uses the table's own
     * border color so the vertical group divider reads at the same weight as the horizontal row separators instead of a
     * heavier foreign rule, and is applied inline (not via a border utility) because the tum-ui table directive styles
     * descendant cells with a higher-specificity arbitrary-variant border-color rule that would otherwise win.
     */
    protected readonly groupDividerStyle = '1px solid var(--p-content-border-color)';

    /** The left divider style for the first column of a group (metadata / content), or null for the rest. */
    protected divider(first: boolean): string | null {
        return first ? this.groupDividerStyle : null;
    }

    protected typeLabelKey(type: string): string {
        return `artemisApp.courseIngestionDashboard.matrix.type_${type}`;
    }

    protected contentLabelKey(contentKey: string): string {
        return `artemisApp.courseIngestionDashboard.matrix.content_${contentKey}`;
    }

    protected contentCensus(course: CourseIndexCensus, contentKey: string): ContentCensus | undefined {
        return course.content?.find((entry) => entry.key === contentKey);
    }

    /** Content cells follow the same green/red rule as measured metadata cells: complete when nothing is missing. */
    protected contentCellStatus(entry: ContentCensus | undefined): CellStatus {
        if (!entry || entry.expected === 0) {
            return 'na';
        }
        return entry.missing === 0 ? 'ok' : 'incomplete';
    }

    protected cellColor(status: CellStatus): string | undefined {
        switch (status) {
            case 'ok':
                return 'var(--success)';
            case 'incomplete':
                return 'var(--danger)';
            case 'unknown':
                return 'var(--warning)';
            default:
                return undefined;
        }
    }

    /** Whether any lecture-content ingestion is currently in flight. */
    protected hasActiveIngestions(): boolean {
        return this.activeIngestions().length > 0;
    }

    /** The name of the step a run is currently on (the running activity), or a "starting" placeholder when none yet. */
    protected currentStepName(run: ActiveIngestion): string {
        const running = (run.activities ?? []).find((activity) => activity.state === 'RUNNING');
        if (running) {
            return running.name;
        }
        return this.translateService.instant('artemisApp.courseIngestionDashboard.active.starting');
    }

    /** The live detail of the currently running step (e.g. "page 3/9"), or empty when there is none. */
    protected currentStepDetail(run: ActiveIngestion): string {
        return (run.activities ?? []).find((activity) => activity.state === 'RUNNING')?.detail ?? '';
    }

    /**
     * Whether a run is stalled: the backend marks it stalled when the unit is in flight but Iris has not reported
     * recently (dispatched but silent, e.g. Iris down). The client-side time check refines it live between polls.
     */
    protected isStalled(run: ActiveIngestion): boolean {
        if (run.stalled) {
            return true;
        }
        if (!run.lastUpdatedAt) {
            return false;
        }
        const lastMillis = Date.parse(run.lastUpdatedAt);
        return !Number.isNaN(lastMillis) && this.nowMillis() - lastMillis > STALL_THRESHOLD_MS;
    }

    /** The phase label for a run when Iris has not reported steps yet (stalled), so the row is not blank. */
    protected phaseLabel(run: ActiveIngestion): string {
        const key = run.phase === 'TRANSCRIBING' ? 'phase_transcribing' : 'phase_ingesting';
        return this.translateService.instant(`artemisApp.courseIngestionDashboard.active.${key}`);
    }

    /** Human-readable time since a run's last status callback, for the stalled warning. */
    protected sinceUpdate(run: ActiveIngestion): string {
        if (!run.lastUpdatedAt) {
            return '';
        }
        const lastMillis = Date.parse(run.lastUpdatedAt);
        return Number.isNaN(lastMillis) ? '' : this.formatDuration(Math.max(0, this.nowMillis() - lastMillis));
    }

    /** The lecture + course context line shown under a run's unit name. */
    protected runContext(run: { lectureName?: string; courseId: number }): string {
        const course = this.courseTitleFor(run.courseId);
        return run.lectureName ? `${run.lectureName} · ${course}` : course;
    }

    protected recentIcon(recent: RecentIngestion) {
        return recent.outcome === 'FAILED' ? this.faTriangleExclamation : this.faCircleCheck;
    }

    protected recentColor(recent: RecentIngestion): string {
        return recent.outcome === 'FAILED' ? 'var(--danger)' : 'var(--success)';
    }

    protected recentTitle(recent: RecentIngestion): string {
        return recent.lectureUnitName || `${this.translateService.instant('artemisApp.courseIngestionDashboard.active.unit')} ${recent.lectureUnitId}`;
    }

    /** Opens the milestone detail drawer for a recent (finished or failed) run. */
    protected openRecent(recent: RecentIngestion): void {
        this.selectedJobId.set(recent.jobId);
    }

    /** Human-readable elapsed time since a run started (e.g. "1m 12s"), or empty when the start time is unknown. */
    protected elapsed(startedAt?: string): string {
        if (!startedAt) {
            return '';
        }
        const startMillis = Date.parse(startedAt);
        if (Number.isNaN(startMillis)) {
            return '';
        }
        return this.formatDuration(Math.max(0, this.nowMillis() - startMillis));
    }

    /** Formats a millisecond duration as "Xm Ys" (or "Ys" under a minute). */
    protected formatDuration(millis?: number): string {
        if (millis === undefined || millis === null) {
            return '';
        }
        if (millis > 0 && millis < 1000) {
            return '<1s';
        }
        const totalSeconds = Math.round(millis / 1000);
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        return minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`;
    }

    /**
     * The ordered step list for a run, built directly from the activities Iris actually reports (no hardcoded step
     * names), so the milestone view matches the real pipeline for any content type and never shows phantom steps.
     */
    protected runSteps(run: { activities?: IngestionActivity[] }, failed = false): DisplayStep[] {
        return (run.activities ?? []).map((activity) => ({
            name: activity.name,
            label: activity.name,
            // A failed run ends without flipping the step it died on off RUNNING; show that step as failed (red X), not
            // as an amber spinner, so the timeline reads as failed rather than perpetually in progress.
            state: failed && activity.state === 'RUNNING' ? 'FAILED' : activity.state,
            durationMillis: activity.durationMillis,
            detail: activity.detail,
        }));
    }

    /** The icon for one pipeline step, by its state: check when done, a spinner while running, a dot when pending. */
    protected stepIcon(state: DisplayStep['state']) {
        switch (state) {
            case 'FINISHED':
                return this.faCheck;
            case 'RUNNING':
                return this.faSpinner;
            case 'FAILED':
                return this.faXmark;
            default:
                return this.faCircle;
        }
    }

    /**
     * Builds the milestone roadmap for a run: each node plus the color of the connector line on its left and right. A
     * segment is green when the step before it is finished, amber when it leads into the currently running step (so the
     * in-progress line and node share one color and never clash with the green "done" line), and grey otherwise.
     */
    protected roadmap(
        run: {
            activities?: IngestionActivity[];
        },
        failed = false,
    ): { label: string; state: DisplayStep['state']; durationMillis?: number; detail?: string; leftColor: string; rightColor: string }[] {
        const steps = this.runSteps(run, failed);
        const segmentColor = (index: number): string => {
            const next = steps[index + 1];
            if (next && next.state === 'RUNNING') {
                return 'var(--warning)';
            }
            if (next && next.state === 'FAILED') {
                return 'var(--danger)';
            }
            if (steps[index].state === 'FINISHED') {
                return 'var(--success)';
            }
            return 'var(--border, #d0d0d0)';
        };
        return steps.map((step, index) => ({
            label: step.label,
            state: step.state,
            durationMillis: step.durationMillis,
            detail: step.detail,
            leftColor: index === 0 ? 'transparent' : segmentColor(index - 1),
            rightColor: index === steps.length - 1 ? 'transparent' : segmentColor(index),
        }));
    }

    /**
     * Live elapsed time spent in the currently running step. Steps run sequentially, so it is the total run elapsed
     * minus the time already accounted for by the finished steps. Ticks with the poll clock, so it counts up live.
     */
    protected runningStepElapsed(run: ActiveIngestion): string {
        if (!run.startedAt) {
            return '';
        }
        const startMillis = Date.parse(run.startedAt);
        if (Number.isNaN(startMillis)) {
            return '';
        }
        const finishedMillis = (run.activities ?? []).filter((activity) => activity.state === 'FINISHED').reduce((sum, activity) => sum + (activity.durationMillis ?? 0), 0);
        return this.formatDuration(Math.max(0, this.nowMillis() - startMillis - finishedMillis));
    }

    /** The color for one pipeline step: green when done, amber while running, red on failure, muted when pending. */
    protected stepColor(state: DisplayStep['state']): string {
        switch (state) {
            case 'FINISHED':
                return 'var(--success)';
            case 'RUNNING':
                return 'var(--warning)';
            case 'FAILED':
                return 'var(--danger)';
            default:
                return 'var(--border-strong, #ccc)';
        }
    }

    /** Thin colored ring for a milestone node (outline style keeps the stepper light rather than heavy solid badges). */
    protected nodeBorder(state: DisplayStep['state']): string {
        return `1.5px solid ${this.stepColor(state)}`;
    }

    /** The display name of the course a run belongs to, taken from the loaded census, or the id as a fallback. */
    protected courseTitleFor(courseId: number): string {
        const course = (this.census() ?? []).find((entry) => entry.courseId === courseId);
        return course?.courseTitle ?? `${courseId}`;
    }

    protected navigateToCourse(courseId: number): void {
        void this.router.navigate(['/course-management', courseId]);
    }

    /** Opens the course-content browser modal and loads what is stored in Weaviate for the course. */
    protected openCourseBrowser(course: CourseIndexCensus): void {
        this.browsedCourse.set(course);
        this.indexedEntities.set([]);
        this.indexedContent.set([]);
        this.indexedMissing.set([]);
        this.contentGaps.set([]);
        this.expandedEntities.set(new Set());
        this.treeOpen.set(new Set());
        this.selectedKey.set('');
        this.entitiesLoading.set(true);
        this.entitiesError.set(false);
        this.dashboardService
            .getIndexedEntities(course.courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (entities) => {
                    this.indexedEntities.set(entities);
                    this.entitiesLoading.set(false);
                },
                error: () => {
                    this.entitiesLoading.set(false);
                    this.entitiesError.set(true);
                },
            });
        this.dashboardService
            .getIndexedContent(course.courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (content) => this.indexedContent.set(content),
                error: () => this.indexedContent.set([]),
            });
        this.dashboardService
            .getMissingEntities(course.courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (missing) => this.indexedMissing.set(missing),
                error: () => this.indexedMissing.set([]),
            });
        this.dashboardService
            .getContentGaps(course.courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (gaps) => this.contentGaps.set(gaps),
                error: () => this.contentGaps.set([]),
            });
    }

    protected onCourseBrowserVisibleChange(visible: boolean): void {
        if (!visible) {
            this.browsedCourse.set(undefined);
            this.indexedEntities.set([]);
            this.indexedContent.set([]);
            this.indexedMissing.set([]);
            this.contentGaps.set([]);
            this.expandedEntities.set(new Set());
            this.treeOpen.set(new Set());
            this.selectedKey.set('');
        }
    }

    // ----- Browser tree: selection + expand state -----

    protected select(key: string): void {
        this.selectedKey.set(key);
    }

    protected isSelected(key: string): boolean {
        return this.selectedKey() === key;
    }

    protected toggleOpen(key: string): void {
        const next = new Set(this.treeOpen());
        if (next.has(key)) {
            next.delete(key);
        } else {
            next.add(key);
        }
        this.treeOpen.set(next);
    }

    protected isOpen(key: string): boolean {
        return this.treeOpen().has(key);
    }

    // ----- Browser tree: assembled structure -----

    /**
     * The metadata types shown as the top-level completeness scoreboard (every measured type, including lectures and
     * lecture units, so an incomplete type surfaces its red dot here and its missing items are reachable). The Lectures
     * section below is a separate browse-the-content drill-down over the indexed lectures.
     */
    protected readonly flatTypes = MATRIX_TYPES;

    /** The lectures for the browsed course, with their units and each unit's content collections, assembled from the data. */
    protected lectureNodes(): { lecture: IndexedEntity; key: string; units: { unit: IndexedEntity; key: string; collections: { key: string; count: number }[] }[] }[] {
        const entities = this.indexedEntities();
        const lectures = entities.filter((entity) => entity.type === 'lecture');
        const units = entities.filter((entity) => entity.type === 'lecture_unit');
        return lectures
            .map((lecture) => ({
                lecture,
                key: `lecture:${lecture.entityId}`,
                units: units
                    .filter((unit) => Number(unit.properties?.['lecture_id']) === lecture.entityId)
                    .map((unit) => ({ unit, key: `unit:${unit.entityId}`, collections: this.unitCollections(unit.entityId) })),
            }))
            .sort((first, second) => (first.lecture.title ?? '').localeCompare(second.lecture.title ?? ''));
    }

    /** The content collections that hold objects for a given unit, with their counts. */
    protected unitCollections(unitId: number): { key: string; count: number }[] {
        return this.indexedContent()
            .map((group) => ({ key: group.key, count: group.objects.filter((object) => Number(object.properties?.['lecture_unit_id']) === unitId).length }))
            .filter((entry) => entry.count > 0);
    }

    /** The indexed objects of a metadata type, for the type detail view. */
    protected objectsOfType(type: string): IndexedEntity[] {
        return this.indexedEntities().filter((entity) => entity.type === type);
    }

    /** The entities of a metadata type that are expected but not indexed, for the type detail's "not ingested" list. */
    protected missingForType(type: string): MissingEntity[] {
        return this.indexedMissing().filter((entity) => entity.type === type);
    }

    /** The stored content objects for one unit and collection, for the collection detail view. */
    protected objectsOfCollection(unitId: number, collectionKey: string): IndexedContentObject[] {
        const group = this.indexedContent().find((entry) => entry.key === collectionKey);
        return group ? group.objects.filter((object) => Number(object.properties?.['lecture_unit_id']) === unitId) : [];
    }

    protected entityById(type: string, id: number): IndexedEntity | undefined {
        return this.indexedEntities().find((entity) => entity.type === type && entity.entityId === id);
    }

    // ----- Browser detail: what the right pane shows for the current selection -----

    protected selectionKind(): 'type' | 'lecture' | 'unit' | 'coll' | 'none' {
        const key = this.selectedKey();
        if (key.startsWith('type:')) {
            return 'type';
        }
        if (key.startsWith('lecture:')) {
            return 'lecture';
        }
        if (key.startsWith('unit:')) {
            return 'unit';
        }
        if (key.startsWith('coll:')) {
            return 'coll';
        }
        return 'none';
    }

    protected selectedType(): string {
        return this.selectedKey().startsWith('type:') ? this.selectedKey().slice('type:'.length) : '';
    }

    protected selectedLectureEntity(): IndexedEntity | undefined {
        return this.selectionKind() === 'lecture' ? this.entityById('lecture', Number(this.selectedKey().slice('lecture:'.length))) : undefined;
    }

    protected selectedUnitEntity(): IndexedEntity | undefined {
        return this.selectionKind() === 'unit' ? this.entityById('lecture_unit', Number(this.selectedKey().slice('unit:'.length))) : undefined;
    }

    /** The parent lecture id of a unit (from its stored metadata), used to open the unit's lecture in Artemis. */
    protected unitLectureId(unit: IndexedEntity): number | undefined {
        const lectureId = Number(unit.properties?.['lecture_id']);
        return Number.isFinite(lectureId) && lectureId > 0 ? lectureId : undefined;
    }

    /** For a selected lecture, its units (as tree nodes) shown as links in the detail. */
    protected selectedLectureUnits(): { unit: IndexedEntity; key: string }[] {
        const lecture = this.selectedLectureEntity();
        if (!lecture) {
            return [];
        }
        return this.indexedEntities()
            .filter((entity) => entity.type === 'lecture_unit' && Number(entity.properties?.['lecture_id']) === lecture.entityId)
            .map((unit) => ({ unit, key: `unit:${unit.entityId}` }));
    }

    /** For a selected unit, its content collections shown as tiles in the detail. */
    protected selectedUnitCollections(): { key: string; count: number; navKey: string }[] {
        const unit = this.selectedUnitEntity();
        if (!unit) {
            return [];
        }
        return this.unitCollections(unit.entityId).map((entry) => ({ ...entry, navKey: `coll:${unit.entityId}:${entry.key}` }));
    }

    /** The content objects for the currently selected collection. */
    protected selectedCollectionObjects(): IndexedContentObject[] {
        if (this.selectionKind() !== 'coll') {
            return [];
        }
        const [, unitId, collectionKey] = this.selectedKey().split(':');
        return this.objectsOfCollection(Number(unitId), collectionKey);
    }

    protected selectedCollectionCrumb(): string[] {
        if (this.selectionKind() !== 'coll') {
            return [];
        }
        const [, unitId, collectionKey] = this.selectedKey().split(':');
        const unit = this.entityById('lecture_unit', Number(unitId));
        return [unit?.title ?? `#${unitId}`, this.translateService.instant(this.contentLabelKeyFor(collectionKey))];
    }

    /** Number of items of a type that are missing (expected but not indexed), from the census. */
    protected typeMissing(type: string): number {
        const course = this.browsedCourse();
        if (!course) {
            return 0;
        }
        return this.typeCensus(course, type)?.missing ?? 0;
    }

    protected contentLabelKeyFor(key: string): string {
        return `artemisApp.courseIngestionDashboard.browser.content_${key}`;
    }

    /** The icon for a metadata type folder, so each searchable-entity type reads at a glance. */
    protected typeIcon(type: string): IconDefinition {
        switch (type) {
            case 'exercise':
                return faListCheck;
            case 'lecture':
                return faBookOpen;
            case 'lecture_unit':
                return faFileLines;
            case 'exam':
                return faGraduationCap;
            case 'faq':
                return faCircleQuestion;
            case 'channel':
                return faHashtag;
            case 'course':
                return faBook;
            case 'post':
                return faComments;
            case 'answer_post':
                return faCommentDots;
            default:
                return faFileLines;
        }
    }

    /** The icon for a content collection node, so slides / transcript / summary / segments read at a glance. */
    protected contentIcon(key: string): IconDefinition {
        switch (key) {
            case 'slides':
                return faImage;
            case 'transcript':
                return faWaveSquare;
            case 'unit_summary':
                return faAlignLeft;
            default:
                return faLayerGroup;
        }
    }

    /** The most recent failed ingestion for a specific unit, so its detail can show a failure banner. */
    protected failureForUnit(unitId: number): RecentIngestion | undefined {
        return this.recentIngestions().find((recent) => recent.lectureUnitId === unitId && recent.outcome === 'FAILED');
    }

    /** Whether a unit is currently being ingested, so the tree can flag it as still processing. */
    protected processingUnit(unitId: number): boolean {
        return this.activeIngestions().some((run) => run.lectureUnitId === unitId);
    }

    /** The content gaps (missing slides / transcript) for a unit, so its detail and dot can show what content is absent. */
    protected gapsForUnit(unitId: number): MissingContent[] {
        return this.contentGaps().filter((gap) => gap.lectureUnitId === unitId);
    }

    /**
     * The completeness status of a unit for the tree dot and detail: failed run wins, then any missing content
     * (expected slides/transcript absent) is incomplete, otherwise complete. Processing is handled separately.
     */
    protected unitStatus(unitId: number): 'failed' | 'incomplete' | 'ok' {
        if (this.failureForUnit(unitId)) {
            return 'failed';
        }
        return this.gapsForUnit(unitId).length > 0 ? 'incomplete' : 'ok';
    }

    /** The dot color for a unit status, reusing the shared cell palette. */
    protected unitDotColor(unitId: number): string {
        switch (this.unitStatus(unitId)) {
            case 'failed':
            case 'incomplete':
                return 'var(--danger)';
            default:
                return 'var(--success)';
        }
    }

    /** Whether a lecture has any unit with a failure or a content gap, so a collapsed lecture still flags the problem. */
    protected lectureIncomplete(lectureId: number): boolean {
        return this.indexedEntities().some(
            (entity) => entity.type === 'lecture_unit' && Number(entity.properties?.['lecture_id']) === lectureId && this.unitStatus(entity.entityId) !== 'ok',
        );
    }

    // ----- Browser detail: stored fields (only the populated ones) -----

    protected fieldExpandKey(prefix: string, id: string | number): string {
        return `${prefix}:${id}`;
    }

    protected isFieldsExpanded(key: string): boolean {
        return this.expandedEntities().has(key);
    }

    protected toggleFields(key: string): void {
        const next = new Set(this.expandedEntities());
        if (next.has(key)) {
            next.delete(key);
        } else {
            next.add(key);
        }
        this.expandedEntities.set(next);
    }

    /** The stored fields with a value, sorted by key. Empty superset fields are dropped so only real data shows. */
    protected populatedFields(properties: Record<string, unknown> | undefined): { key: string; value: string }[] {
        return Object.entries(properties ?? {})
            .map(([key, value]) => ({ key, value: this.formatValue(value) }))
            .filter((entry) => entry.value.trim() !== '')
            .sort((first, second) => first.key.localeCompare(second.key));
    }

    /** A compact label for one content object, derived from whatever identifying fields it stores. */
    protected contentObjectLabel(object: IndexedContentObject, index: number): string {
        const props = object.properties ?? {};
        const page = props['page_number'] ?? props['display_page_number'];
        const start = props['segment_start_time'];
        if (typeof page === 'number' || typeof page === 'string') {
            return `Page ${page}`;
        }
        if (typeof start === 'number' || typeof start === 'string') {
            return `Segment @ ${start}s`;
        }
        return `#${index + 1}`;
    }

    /** Recent failed ingestions for a course, newest first, for the browser modal's failures section. */
    protected failedForCourse(courseId: number): RecentIngestion[] {
        return this.recentIngestions().filter((recent) => recent.courseId === courseId && recent.outcome === 'FAILED');
    }

    /** How many measurable types (metadata + content) are incomplete for a course, for the browser header summary. */
    protected incompleteTypeCount(course: CourseIndexCensus): number {
        const meta = this.types.filter((type) => this.cellStatus(this.typeCensus(course, type)) === 'incomplete').length;
        const content = this.contentTypes.filter((contentType) => this.contentCellStatus(this.contentCensus(course, contentType)) === 'incomplete').length;
        return meta + content;
    }

    private formatValue(value: unknown): string {
        if (value === null || value === undefined) {
            return '';
        }
        if (typeof value === 'string') {
            return value;
        }
        if (typeof value === 'number' || typeof value === 'boolean' || typeof value === 'bigint') {
            return String(value);
        }
        return JSON.stringify(value);
    }

    protected navigateToLecture(courseId: number, lectureId: number): void {
        void this.router.navigate(['/course-management', courseId, 'lectures', lectureId]);
    }

    /**
     * Opens the lecture unit in Artemis. Lecture units live on their lecture's unit-management page, which is the
     * closest unit-level destination that works for every unit type, so this lands the admin on the unit's page.
     */
    protected navigateToUnit(courseId: number, lectureId: number): void {
        void this.router.navigate(['/course-management', courseId, 'lectures', lectureId, 'unit-management']);
    }

    /**
     * Opens the Artemis page for whatever the browser modal currently has selected: the lecture for a lecture, the
     * unit-management page for a unit or one of its collections, and the course otherwise. This makes the modal's open
     * action follow the selection instead of always opening the course.
     */
    protected openSelection(): void {
        const course = this.browsedCourse();
        if (!course) {
            return;
        }
        const courseId = course.courseId;
        switch (this.selectionKind()) {
            case 'lecture': {
                const lecture = this.selectedLectureEntity();
                if (lecture) {
                    this.navigateToLecture(courseId, lecture.entityId);
                    return;
                }
                break;
            }
            case 'unit': {
                const lectureId = this.selectedUnitEntity() ? this.unitLectureId(this.selectedUnitEntity()!) : undefined;
                if (lectureId) {
                    this.navigateToUnit(courseId, lectureId);
                    return;
                }
                break;
            }
            case 'coll': {
                const unit = this.entityById('lecture_unit', Number(this.selectedKey().split(':')[1]));
                const lectureId = unit ? this.unitLectureId(unit) : undefined;
                if (lectureId) {
                    this.navigateToUnit(courseId, lectureId);
                    return;
                }
                break;
            }
        }
        this.navigateToCourse(courseId);
    }

    /** The translation key for the browser modal's contextual open button, matching the current selection. */
    protected openSelectionLabelKey(): string {
        switch (this.selectionKind()) {
            case 'lecture':
                return 'artemisApp.courseIngestionDashboard.browser.openLecture';
            case 'unit':
            case 'coll':
                return 'artemisApp.courseIngestionDashboard.browser.openUnit';
            default:
                return 'artemisApp.courseIngestionDashboard.browser.openCourse';
        }
    }

    /** Opens the milestone detail drawer for a run. */
    protected openRun(run: ActiveIngestion): void {
        this.selectedJobId.set(run.jobId);
    }

    /** Clears the selected run when the drawer is dismissed. */
    protected onDrawerVisibleChange(visible: boolean): void {
        if (!visible) {
            this.selectedJobId.set(undefined);
        }
    }

    /** Display title for a run: its lecture unit name, or the unit id as a fallback. */
    protected runTitle(run: ActiveIngestion): string {
        return run.lectureUnitName || `${this.translateService.instant('artemisApp.courseIngestionDashboard.active.unit')} ${run.lectureUnitId}`;
    }

    private translate(key: string): string {
        return this.translateService.instant(`artemisApp.courseIngestionDashboard.matrix.${key}`);
    }

    private courseRank(course: CourseIndexCensus): number {
        return STATUS_ORDER.indexOf(this.courseWorst(course));
    }
}
