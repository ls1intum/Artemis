import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import {
    TumUiButtonComponent,
    TumUiInputDirective,
    TumUiMessageComponent,
    TumUiPaginatorComponent,
    TumUiPanelComponent,
    TumUiSelectButtonComponent,
    TumUiTableDirective,
} from '@tumaet/ui-angular';
import { IconDefinition, faSort, faSortDown, faSortUp, faSync } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { IngestionCoverage, IngestionCoverageStatus } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

/** The metadata entity types, in the order shown under the "Entity metadata" column group. */
const METADATA_TYPES = ['exercise', 'lecture', 'lecture_unit', 'exam', 'faq', 'channel', 'course'] as const;

/** The lecture-content types, in the order shown under the "Lecture content" column group. */
const CONTENT_TYPES = ['slides', 'transcript', 'segment_summary', 'unit_summary'] as const;

/** The sortable columns. Worst-first is cross-course, so it is served from the stored projection. */
type SortMode = 'name' | 'release' | 'worstFirst';

type SortDirection = 'asc' | 'desc';

/** Each sort mode's backend field (stored and live variants), its default direction, and whether it needs the stored projection. */
const SORT_CONFIG: Record<SortMode, { storedField: string; liveField: string; defaultDirection: SortDirection; requiresStored: boolean }> = {
    name: { storedField: 'courseTitle', liveField: 'title', defaultDirection: 'asc', requiresStored: false },
    release: { storedField: 'releaseDate', liveField: 'startDate', defaultDirection: 'desc', requiresStored: false },
    worstFirst: { storedField: 'coverageGapScore', liveField: 'title', defaultDirection: 'desc', requiresStored: true },
};

/** The semantic text colour for each coverage status; used for both the status dot and its label. */
const STATUS_CLASS: Record<IngestionCoverageStatus, string> = {
    COMPLETE: 'text-state-success',
    INCOMPLETE: 'text-state-danger',
    EMPTY: 'text-muted-color',
};

/** The left divider drawn before each column group. */
const GROUP_DIVIDER = 'border-l border-surface-200 dark:border-surface-700';

/** Debounce for the title search, so typing does not fire a request on every keystroke. */
const SEARCH_DEBOUNCE_MS = 300;

interface SelectOption {
    label: string;
    value: string;
}

/**
 * The per-course coverage matrix: one row per course with a cell per indexed type under two column groups (entity
 * metadata and lecture content), a release-date column, and a status dot. The header columns sort, and a toolbar filters
 * by status, searches by title, refreshes, and paginates.
 * <p>
 * Cross-course modes (worst-first, status filter) read the stored projection ({@code coverage}); the default name/release
 * /search view is computed live for the visible page ({@code coverage/page}). Both return the same row shape, so the
 * matrix renders identically from either.
 */
@Component({
    selector: 'jhi-course-ingestion-coverage-table',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        FaIconComponent,
        TumUiPanelComponent,
        TumUiTableDirective,
        TumUiInputDirective,
        TumUiMessageComponent,
        TumUiButtonComponent,
        TumUiPaginatorComponent,
        TumUiSelectButtonComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
    ],
    templateUrl: './course-ingestion-coverage-table.component.html',
    // The tum-ui input already turns its border to the focus colour on focus; drop the kit's extra offset outline so
    // the search box shows a single clean focus ring instead of a border plus a detached outer ring.
    styles: `
        input[tumUiInput]:focus-visible {
            outline: none;
        }
    `,
})
export class CourseIngestionCoverageTableComponent implements OnInit {
    private readonly dashboardService = inject(CourseIngestionDashboardService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly translateService = inject(TranslateService);

    /** Raw search input; debounced before it triggers a reload. */
    private readonly searchInput$ = new Subject<string>();

    protected readonly faSync = faSync;
    protected readonly metadataTypes = METADATA_TYPES;
    protected readonly contentTypes = CONTENT_TYPES;
    /** Metadata then content types, for the single cell/type-header loop (the group headers use the two arrays above). */
    protected readonly allTypes = [...METADATA_TYPES, ...CONTENT_TYPES];
    protected readonly groupDivider = GROUP_DIVIDER;

    readonly rows = signal<IngestionCoverage[]>([]);
    readonly totalRecords = signal(0);
    readonly loading = signal(true);
    readonly error = signal(false);
    readonly refreshing = signal(false);
    readonly lastUpdated = signal<string | undefined>(undefined);

    readonly page = signal(0);
    readonly pageSize = signal(20);
    readonly sortMode = signal<SortMode>('name');
    readonly sortDirection = signal<SortDirection>('asc');
    readonly statusFilter = signal<IngestionCoverageStatus | undefined>(undefined);
    readonly activeFilter = signal<boolean | undefined>(undefined);
    readonly search = signal('');

    /** The active filter as the string the select-button binds to (empty = any). */
    protected readonly activeFilterValue = computed(() => (this.activeFilter() === undefined ? '' : String(this.activeFilter())));

    /**
     * Rebuilds the filter labels when the language changes. A field initialiser resolves once at construction, before
     * the translations are loaded and never again, so the labels stayed on the English fallback in a German session.
     */
    private readonly languageChange = toSignal(this.translateService.onLangChange);

    protected readonly statusOptions = computed<SelectOption[]>(() => {
        this.languageChange();
        return [
            { label: this.translateService.instant('artemisApp.courseIngestionDashboard.matrix.status.all'), value: '' },
            { label: this.translateService.instant('artemisApp.courseIngestionDashboard.status.COMPLETE'), value: 'COMPLETE' },
            { label: this.translateService.instant('artemisApp.courseIngestionDashboard.status.INCOMPLETE'), value: 'INCOMPLETE' },
            { label: this.translateService.instant('artemisApp.courseIngestionDashboard.status.EMPTY'), value: 'EMPTY' },
        ];
    });

    protected readonly activeOptions = computed<SelectOption[]>(() => {
        this.languageChange();
        return [
            { label: this.translateService.instant('artemisApp.courseIngestionDashboard.matrix.active.all'), value: '' },
            { label: this.translateService.instant('artemisApp.courseIngestionDashboard.matrix.active.active'), value: 'true' },
            { label: this.translateService.instant('artemisApp.courseIngestionDashboard.matrix.active.inactive'), value: 'false' },
        ];
    });

    constructor() {
        this.searchInput$.pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef)).subscribe((term) => {
            this.search.set(term.trim());
            this.page.set(0);
            this.load();
        });
    }

    ngOnInit(): void {
        this.load();
    }

    private load(): void {
        this.loading.set(true);
        this.error.set(false);
        const page = this.page();
        const size = this.pageSize();
        const config = SORT_CONFIG[this.sortMode()];
        const direction = this.sortDirection();
        // A status/active filter or a cross-course sort (worst-first) must read the stored projection.
        const useStored = config.requiresStored || this.statusFilter() !== undefined || this.activeFilter() !== undefined;
        const request$ = useStored
            ? this.dashboardService.getStoredCoverage({ page, size, sort: `${config.storedField},${direction}`, status: this.statusFilter(), active: this.activeFilter() })
            : this.dashboardService.getLiveCoveragePage({ page, size, sort: `${config.liveField},${direction}`, search: this.search() || undefined });

        request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
            next: (result) => {
                this.rows.set(result.content);
                this.totalRecords.set(result.totalElements);
                this.lastUpdated.set(result.content[0]?.computedAt);
                this.loading.set(false);
            },
            error: () => {
                this.rows.set([]);
                this.totalRecords.set(0);
                this.error.set(true);
                this.loading.set(false);
            },
        });
    }

    /** Sorts by the given column, toggling direction if it is already the active sort, and reloads from the first page. */
    protected toggleSort(mode: SortMode): void {
        if (this.sortMode() === mode) {
            this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
        } else {
            this.sortMode.set(mode);
            this.sortDirection.set(SORT_CONFIG[mode].defaultDirection);
        }
        this.page.set(0);
        this.load();
    }

    /** The sort arrow for a column header: up/down when it is the active sort, a neutral icon otherwise. */
    protected sortIcon(mode: SortMode): IconDefinition {
        if (this.sortMode() !== mode) {
            return faSort;
        }
        return this.sortDirection() === 'asc' ? faSortUp : faSortDown;
    }

    protected onStatusChange(value: string | undefined): void {
        this.statusFilter.set(value ? (value as IngestionCoverageStatus) : undefined);
        this.page.set(0);
        this.load();
    }

    protected onActiveChange(value: string | undefined): void {
        this.activeFilter.set(value ? value === 'true' : undefined);
        this.page.set(0);
        this.load();
    }

    protected onSearchInput(term: string): void {
        this.searchInput$.next(term);
    }

    protected onPageChange(page: number): void {
        this.page.set(page);
        this.load();
    }

    protected onRefresh(): void {
        this.refreshing.set(true);
        this.dashboardService
            .refreshCoverage()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.refreshing.set(false);
                    this.load();
                },
                error: () => this.refreshing.set(false),
            });
    }

    /**
     * The display for one coverage cell, computed once: the text ("indexed/expected", or a dash when nothing is expected
     * or present) and the semantic colour (danger when something is missing, success when complete, muted when empty).
     */
    protected cell(row: IngestionCoverage, type: string): { text: string; class: string } {
        const count = row.typeCounts.find((entry) => entry.type === type);
        if (!count || (count.expected === 0 && count.indexed === 0)) {
            return { text: '–', class: 'text-muted-color' };
        }
        return {
            text: `${count.indexed}/${count.expected}`,
            class: count.missing > 0 ? 'text-state-danger' : 'text-state-success',
        };
    }

    /** The group divider drawn before the first metadata column and the first content column, keyed by cell index. */
    protected dividerAt(index: number): string {
        return index === 0 || index === METADATA_TYPES.length ? GROUP_DIVIDER : '';
    }

    protected statusClass(status: IngestionCoverageStatus): string {
        return STATUS_CLASS[status];
    }
}
