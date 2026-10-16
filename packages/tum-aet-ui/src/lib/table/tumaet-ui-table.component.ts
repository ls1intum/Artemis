import { NgTemplateOutlet } from '@angular/common';
import { CdkTable, CdkTableModule } from '@angular/cdk/table';
import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    TemplateRef,
    TrackByFunction,
    afterNextRender,
    booleanAttribute,
    computed,
    effect,
    inject,
    input,
    numberAttribute,
    output,
    signal,
    untracked,
    viewChild,
} from '@angular/core';
import { get } from 'lodash-es';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faCircleQuestion, faMagnifyingGlass, faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiPaginatorComponent } from '../paginator/tumaet-ui-paginator.component';
import { CellRendererParams, ColumnDef, TumAetUiSortDirection, TumAetUiSortState, TumAetUiTableQueryEvent } from './tumaet-ui-table.types';
import { TumAetUiTranslatePipe } from '../i18n/tumaet-ui-translate.pipe';
import { TumAetUiTooltipDirective } from '../tooltip/tumaet-ui-tooltip.directive';

const ACTIONS_COLUMN = '__tum_ui_actions__';
const SEARCH_DEBOUNCE_MS = 300;

const HIDE_BELOW_CLASSES: Record<NonNullable<ColumnDef<unknown>['hideBelow']>, string> = {
    sm: 'tumaet:hidden tumaet:sm:table-cell',
    md: 'tumaet:hidden tumaet:md:table-cell',
    lg: 'tumaet:hidden tumaet:lg:table-cell',
    xl: 'tumaet:hidden tumaet:xl:table-cell',
    '2xl': 'tumaet:hidden tumaet:2xl:table-cell',
};

/** Server-driven table whose consumer owns rows and responds to query changes. */
@Component({
    selector: 'tumaet-ui-table',
    templateUrl: './tumaet-ui-table.component.html',
    styleUrl: './tumaet-ui-table.component.scss',
    imports: [CdkTableModule, NgTemplateOutlet, FaIconComponent, TumAetUiTranslatePipe, TumAetUiPaginatorComponent, TumAetUiTooltipDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiTableComponent<T> {
    private readonly destroyRef = inject(DestroyRef);
    /** Columns displayed in declaration order. Nested field paths use lodash path syntax. */
    readonly columns = input.required<ColumnDef<T>[]>();
    /** Rows for the current page. Sorting and filtering are not applied locally. */
    readonly rows = input.required<T[]>();
    readonly totalRecords = input(0, { transform: numberAttribute });
    readonly loading = input(false, { transform: booleanAttribute });
    /** Optional action template receiving the row as its implicit value. */
    readonly rowActions = input<TemplateRef<{ $implicit: T }> | undefined>(undefined);
    /** Predicate identifying rows to highlight. */
    readonly rowHighlighted = input<((row: T) => boolean) | undefined>(undefined);

    /** Identity function forwarded to the CDK table. */
    readonly trackBy = input<TrackByFunction<T> | undefined>(undefined);
    readonly striped = input(false, { transform: booleanAttribute });
    readonly scrollable = input(false, { transform: booleanAttribute });
    readonly scrollHeight = input<string | undefined>(undefined);
    readonly showSearch = input(true, { transform: booleanAttribute });
    readonly searchPlaceholder = input('tumAetUi.table.searchPlaceholder');
    readonly emptyMessage = input('tumAetUi.table.noResults');
    readonly pageSize = input(50, { transform: numberAttribute });
    readonly pageSizeOptions = input<number[]>([10, 20, 50, 100, 200]);

    readonly showRowsPerPage = input(true, { transform: booleanAttribute });

    readonly showCurrentPageReport = input(true, { transform: booleanAttribute });
    readonly initialSortField = input<string | undefined>(undefined);
    readonly initialSortDirection = input<TumAetUiSortDirection>('asc');

    /** Requests a zero-based page with the active page size, sort, and search term. */
    readonly dataRequest = output<TumAetUiTableQueryEvent>();

    protected readonly ACTIONS_COLUMN = ACTIONS_COLUMN;
    protected readonly faCircleQuestion = faCircleQuestion;
    protected readonly faMagnifyingGlass = faMagnifyingGlass;
    protected readonly faSort = faSort;
    protected readonly faSortDown = faSortDown;
    protected readonly faSortUp = faSortUp;

    private readonly cdkTable = viewChild(CdkTable);

    private readonly page = signal(0);
    private readonly pageSizeState = signal<number | undefined>(undefined);
    private readonly sortState = signal<TumAetUiSortState | undefined>(undefined);
    private readonly searchTerm = signal('');
    private searchTimer?: ReturnType<typeof setTimeout>;

    protected readonly effectivePageSize = computed(() => this.pageSizeState() ?? this.pageSize());
    protected readonly currentPage = computed(() => this.page());
    protected readonly effectiveTrackBy = computed<TrackByFunction<T>>(() => this.trackBy() ?? ((_, item) => item));

    protected readonly displayedColumns = computed(() => {
        const names = this.columns().map((col, index) => this.columnName(col, index));
        return this.rowActions() ? [...names, ACTIONS_COLUMN] : names;
    });

    protected readonly tableClasses = computed(() => {
        const base = 'tumaet:w-full tumaet:border-collapse tumaet:text-sm';
        return this.striped() ? `${base} tumaet:[&_tbody_tr:nth-child(odd)]:bg-table-striped-background` : base;
    });

    constructor() {
        afterNextRender(() => {
            const field = this.initialSortField();
            if (field) {
                this.sortState.set({ field, direction: this.initialSortDirection() });
            }
            this.emitDataRequest();
        });
        effect(() => {
            this.displayedColumns();
            this.cdkTable()?.renderRows();
        });
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

    /** Jump back to the first page and re-request. For consumers that own filtering themselves (`showSearch` off). */
    resetPage(): void {
        if (this.page() === 0) {
            return;
        }
        this.page.set(0);
        this.emitDataRequest();
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

    protected columnVisibilityClasses(col: ColumnDef<T>): string {
        return col.hideBelow ? HIDE_BELOW_CLASSES[col.hideBelow] : '';
    }

    /**
     * A header that cannot wrap is a floor the column can never go below, so a long one costs its full width in every
     * layout however little the cells under it hold. `wrapHeader` trades a two-line heading for that width.
     */
    protected headerCellClasses(col: ColumnDef<T>): string {
        const whitespace = col.wrapHeader ? '' : 'tumaet:whitespace-nowrap';
        return `${this.columnVisibilityClasses(col)} ${whitespace}`.trim();
    }

    protected ariaSortFor(col: ColumnDef<T>): 'ascending' | 'descending' | 'none' | undefined {
        if (!col.sort || !col.field) {
            return undefined;
        }
        const sort = this.sortState();
        if (!sort || sort.field !== col.field) {
            return 'none';
        }
        return sort.direction === 'asc' ? 'ascending' : 'descending';
    }
    protected sortDirection(col: ColumnDef<T>): 'none' | TumAetUiSortDirection {
        const sort = this.sortState();
        if (!sort || sort.field !== col.field) {
            return 'none';
        }
        return sort.direction;
    }

    protected sortIcon(col: ColumnDef<T>): IconDefinition {
        switch (this.sortDirection(col)) {
            case 'asc':
                return this.faSortUp;
            case 'desc':
                return this.faSortDown;
            default:
                return this.faSort;
        }
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
            pageIndex: this.page(),
            pageSize: this.effectivePageSize(),
            sort: this.sortState(),
            searchTerm: this.searchTerm().trim() || undefined,
        });
    }
}
