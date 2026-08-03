import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faDatabase, faLayerGroup, faSync, faXmark } from '@fortawesome/free-solid-svg-icons';
import { CourseIngestionDashboardService } from './course-ingestion-dashboard.service';
import { ContentCensus, CourseIndexCensus, IndexOverview, TypeIndexCensus } from './course-ingestion-dashboard.model';

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

    protected readonly faSync = faSync;
    protected readonly faDatabase = faDatabase;
    protected readonly faLayerGroup = faLayerGroup;
    protected readonly faXmark = faXmark;
    protected readonly types = MATRIX_TYPES;
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

    private translate(key: string): string {
        return this.translateService.instant(`artemisApp.courseIngestionDashboard.matrix.${key}`);
    }

    private courseRank(course: CourseIndexCensus): number {
        return STATUS_ORDER.indexOf(this.courseWorst(course));
    }
}
