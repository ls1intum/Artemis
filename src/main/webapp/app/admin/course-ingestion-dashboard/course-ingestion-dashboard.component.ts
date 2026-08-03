import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of, switchMap, timer } from 'rxjs';
import { DatePipe, DecimalPipe } from '@angular/common';
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
import { faCheck, faChevronRight, faCircle, faCircleCheck, faDatabase, faLayerGroup, faSpinner, faSync, faTriangleExclamation, faXmark } from '@fortawesome/free-solid-svg-icons';
import { CourseIngestionDashboardService } from './course-ingestion-dashboard.service';
import {
    ActiveIngestion,
    ContentCensus,
    CourseIndexCensus,
    DisplayStep,
    IndexOverview,
    IngestionActivity,
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
    protected readonly types = MATRIX_TYPES;

    // Live active ingestions, polled every few seconds so the milestone view auto-updates. A ticking clock signal
    // drives the elapsed-time display between polls.
    readonly activeIngestions = signal<ActiveIngestion[]>([]);
    readonly recentIngestions = signal<RecentIngestion[]>([]);
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
                switchMap(() =>
                    forkJoin({
                        active: this.dashboardService.getActiveIngestions().pipe(catchError(() => of([] as ActiveIngestion[]))),
                        recent: this.dashboardService.getRecentIngestions().pipe(catchError(() => of([] as RecentIngestion[]))),
                    }),
                ),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe(({ active, recent }) => {
                this.activeIngestions.set(active);
                this.recentIngestions.set(recent);
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

    /** Adds a left divider border to the given base classes for the first column of a group, to visually split groups. */
    protected divider(first: boolean, base: string): string {
        return first ? `${base} border-l border-surface-300 dark:border-surface-600` : base;
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

    /** Whether a run looks stalled: no status callback for longer than the threshold (catches a killed Iris quickly). */
    protected isStalled(run: ActiveIngestion): boolean {
        if (!run.lastUpdatedAt) {
            return false;
        }
        const lastMillis = Date.parse(run.lastUpdatedAt);
        return !Number.isNaN(lastMillis) && this.nowMillis() - lastMillis > STALL_THRESHOLD_MS;
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
    protected runSteps(run: { activities?: IngestionActivity[] }): DisplayStep[] {
        return (run.activities ?? []).map((activity) => ({
            name: activity.name,
            label: activity.name,
            state: activity.state,
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
    protected roadmap(run: {
        activities?: IngestionActivity[];
    }): { label: string; state: DisplayStep['state']; durationMillis?: number; detail?: string; leftColor: string; rightColor: string }[] {
        const steps = this.runSteps(run);
        const segmentColor = (index: number): string => {
            const next = steps[index + 1];
            if (next && next.state === 'RUNNING') {
                return 'var(--warning)';
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

    protected navigateToLecture(courseId: number, lectureId: number): void {
        void this.router.navigate(['/course-management', courseId, 'lectures', lectureId]);
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
