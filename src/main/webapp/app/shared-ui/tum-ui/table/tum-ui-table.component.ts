import { NgTemplateOutlet } from '@angular/common';
import { CdkTable, CdkTableModule } from '@angular/cdk/table';
import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    TemplateRef,
    TrackByFunction,
    afterNextRender,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
    untracked,
    viewChild,
} from '@angular/core';
import { get } from 'lodash-es';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiPaginatorComponent } from 'app/shared-ui/tum-ui/paginator/tum-ui-paginator.component';
import { CellRendererParams, ColumnDef, TumUiSortDirection, TumUiSortState, TumUiTableQueryEvent } from 'app/shared-ui/tum-ui/table/tum-ui-table.types';

const ACTIONS_COLUMN = '__tum_ui_actions__';
const SEARCH_DEBOUNCE_MS = 300;

/**
 * Owned data table on Angular CDK (@angular/cdk/table), part of the tum-aet-ui kit.
 *
 * Signal-based, PrimeNG-free. Renders dynamic columns from a {@link ColumnDef} array, supports
 * single-column server-side sort, a debounced global search, and pagination (via tum-ui-paginator),
 * emitting a {@link TumUiTableQueryEvent} the caller feeds into its server query. Styled with Artemis
 * token utilities only.
 */
@Component({
    selector: 'tum-ui-table',
    templateUrl: './tum-ui-table.component.html',
    styleUrl: './tum-ui-table.component.scss',
    imports: [CdkTableModule, NgTemplateOutlet, FaIconComponent, TranslateDirective, ArtemisTranslatePipe, TumUiPaginatorComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTableComponent<T> {
    readonly columns = input.required<ColumnDef<T>[]>();
    readonly rows = input.required<T[]>();
    readonly totalRecords = input(0);
    readonly loading = input(false);
    readonly rowActions = input<TemplateRef<{ $implicit: T }> | undefined>(undefined);
    /**
     * Row identity for CDK diffing. Without it a server reload hands CDK a fresh array of new objects and it
     * rebuilds every row (losing DOM state / focus); pass a stable-id function (e.g. `(_, row) => row.id`) so
     * unchanged rows are reused. Defaults to CDK's object-identity tracking when omitted.
     */
    readonly trackBy = input<TrackByFunction<T> | undefined>(undefined);
    readonly striped = input(false);
    readonly scrollable = input(false);
    readonly scrollHeight = input<string | undefined>(undefined);
    readonly showSearch = input(true);
    readonly searchPlaceholder = input('artemisApp.course.exercise.search.searchPlaceholder');
    readonly emptyMessage = input('artemisApp.dataTable.search.noResults');
    readonly pageSize = input(50);
    readonly pageSizeOptions = input<number[]>([10, 20, 50, 100, 200]);
    /** Forwarded to the embedded paginator. Set false to hide the rows-per-page select (e.g. the legacy
     *  table-view `hidePageSizeOptions`) so a fixed-page-size table shows no pointless single-option dropdown. */
    readonly showRowsPerPage = input(true);
    /** Forwarded to the embedded paginator's "Showing X to Y of Z" report. */
    readonly showCurrentPageReport = input(true);
    readonly initialSortField = input<string | undefined>(undefined);
    readonly initialSortDirection = input<TumUiSortDirection>('asc');

    readonly dataRequest = output<TumUiTableQueryEvent>();

    protected readonly ACTIONS_COLUMN = ACTIONS_COLUMN;
    protected readonly faMagnifyingGlass = faMagnifyingGlass;

    private readonly destroyRef = inject(DestroyRef);
    private readonly cdkTable = viewChild(CdkTable);

    private readonly page = signal(0);
    private readonly pageSizeState = signal<number | undefined>(undefined);
    private readonly sortState = signal<TumUiSortState | undefined>(undefined);
    private readonly searchTerm = signal('');
    private searchTimer?: ReturnType<typeof setTimeout>;

    protected readonly effectivePageSize = computed(() => this.pageSizeState() ?? this.pageSize());
    protected readonly currentPage = computed(() => this.page());
    // CdkTable's `trackBy` input is non-nullable, so fall back to identity tracking (CDK's own default) when no
    // trackBy is provided.
    protected readonly effectiveTrackBy = computed<TrackByFunction<T>>(() => this.trackBy() ?? ((_, item) => item));

    protected readonly displayedColumns = computed(() => {
        const names = this.columns().map((col, index) => this.columnName(col, index));
        return this.rowActions() ? [...names, ACTIONS_COLUMN] : names;
    });

    protected readonly tableClasses = computed(() => {
        const base = 'w-full border-collapse text-sm';
        // Zebra striping matched to PrimeNG's p-table: ODD rows tinted (surface-50 light / surface-950 dark),
        // even rows transparent. Expressed as a literal arbitrary variant so Tailwind generates it (@source scans .ts).
        return this.striped() ? `${base} [&_tbody_tr:nth-child(odd)]:bg-surface-50 dark:[&_tbody_tr:nth-child(odd)]:bg-surface-950` : base;
    });

    constructor() {
        // Seed sort from initial inputs, then emit one lazy load after first render (parity with PrimeNG's on-init load).
        afterNextRender(() => {
            const field = this.initialSortField();
            if (field) {
                this.sortState.set({ field, direction: this.initialSortDirection() });
            }
            this.emitDataRequest();
        });
        // Dynamic-column safety: re-render CdkTable when the displayed column set changes.
        effect(() => {
            this.displayedColumns();
            this.cdkTable()?.renderRows();
        });
        // Clamp the page when totalRecords shrinks below the current page (e.g. after deleting the last row of
        // the last page, the consumer reloads the same — now out-of-range — page). Re-emit for the last valid
        // page so the grid never strands on an empty out-of-range page. Matches PrimeNG's paginator auto-clamp
        // and converges in one step (the corrected page is in range, so it does not re-fire).
        effect(() => {
            const total = this.totalRecords();
            const rows = this.effectivePageSize();
            const lastPage = total > 0 ? Math.ceil(total / Math.max(1, rows)) - 1 : 0;
            if (this.page() > lastPage) {
                untracked(() => {
                    this.page.set(lastPage);
                    this.emitDataRequest();
                });
            }
        });
        this.destroyRef.onDestroy(() => clearTimeout(this.searchTimer));
    }

    protected columnName(col: ColumnDef<T>, index: number): string {
        return col.field ?? col.headerKey ?? col.header ?? `col-${index}`;
    }

    protected resolveValue(row: T, col: ColumnDef<T>): unknown {
        return col.field ? get(row, col.field) : undefined;
    }

    protected cellParams(row: T, col: ColumnDef<T>, rowIndex: number): CellRendererParams<T> {
        return { data: row, col, value: this.resolveValue(row, col), rowIndex };
    }

    protected ariaSortFor(col: ColumnDef<T>): 'ascending' | 'descending' | 'none' | undefined {
        // Non-sortable columns get no aria-sort at all; sortable ones always expose it ('none' when not the
        // active sort) so assistive tech announces them as sortable (parity with PrimeNG's pSortableColumn).
        if (!col.sort || !col.field) {
            return undefined;
        }
        const sort = this.sortState();
        if (!sort || sort.field !== col.field) {
            return 'none';
        }
        return sort.direction === 'asc' ? 'ascending' : 'descending';
    }

    /** Current sort direction for a column, driving the inline sort-icon SVG (matches PrimeNG's p-sortIcon). */
    protected sortDirection(col: ColumnDef<T>): 'none' | TumUiSortDirection {
        const sort = this.sortState();
        if (!sort || sort.field !== col.field) {
            return 'none';
        }
        return sort.direction;
    }

    protected onSortClick(col: ColumnDef<T>): void {
        if (!col.sort || !col.field) {
            return;
        }
        const current = this.sortState();
        this.sortState.set(
            current && current.field === col.field ? { field: col.field, direction: current.direction === 'asc' ? 'desc' : 'asc' } : { field: col.field, direction: 'asc' },
        );
        this.page.set(0);
        this.emitDataRequest();
    }

    protected onSearchInput(value: string): void {
        clearTimeout(this.searchTimer);
        this.searchTimer = setTimeout(() => {
            this.searchTerm.set(value);
            this.page.set(0);
            this.emitDataRequest();
        }, SEARCH_DEBOUNCE_MS);
    }

    protected onPageChange(page: number): void {
        this.page.set(page);
        this.emitDataRequest();
    }

    protected onPageSizeChange(size: number): void {
        this.pageSizeState.set(size);
        this.page.set(0);
        this.emitDataRequest();
    }

    private emitDataRequest(): void {
        this.dataRequest.emit({
            page: this.page(),
            pageSize: this.effectivePageSize(),
            sort: this.sortState(),
            searchTerm: this.searchTerm().trim() || undefined,
        });
    }
}
