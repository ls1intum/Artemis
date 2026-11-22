import { NgClass, NgComponentOutlet, NgTemplateOutlet } from '@angular/common';
import {
    Component,
    DestroyRef,
    ElementRef,
    TemplateRef,
    Type,
    ViewEncapsulation,
    afterNextRender,
    afterRenderEffect,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
    untracked,
    viewChild,
    viewChildren,
} from '@angular/core';
import { get } from 'lodash-es';
import {
    TumAetUiCheckboxComponent,
    TumAetUiInputDirective,
    TumAetUiPaginatorComponent,
    TumAetUiProgressSpinnerComponent,
    TumAetUiRadioButtonComponent,
    TumAetUiTableDirective,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleQuestion, faComment, faHashtag, faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { TableViewLazyLoadEvent, TableViewPageEvent } from './table-view-query';
export type { TableViewLazyLoadEvent } from './table-view-query';

let nextTableSelectionId = 0;

export interface ColumnDef<T> {
    /** Top-level key of T (e.g. `'name'`) or dot-path to a nested field (e.g. `'owner.name'`). Dot-paths are resolved via lodash `get` for value resolution and passed as-is to the table for sorting and filtering. */
    field?: string;
    header?: string;
    headerKey?: string;
    /** PrimeIcons CSS class string, e.g. 'pi pi-hashtag'. Rendered as <i> in the column header. */
    headerIcon?: string;
    /** Translation key shown as a the table tooltip on the column header. */
    headerTooltip?: string;
    width?: string;
    sort?: boolean;
    filter?: boolean;
    filterType?: string;
    /** Pin this column so it stays visible while the table scrolls horizontally. Requires `scrollable: true` in the table options. */
    frozen?: boolean;
    /** Which side the frozen column sticks to. Only used when `frozen` is true. Default: 'left'. */
    alignFrozen?: 'left' | 'right';
    /** Render the cell using a parent-defined template. Receives {@link CellRendererParams} as `$implicit`. Takes priority over `cellRenderer`. */
    templateRef?: CellTemplateRef<T>;
    cellRenderer?: Type<unknown>;
    /**
     * Custom comparator for client-side sorting of this column. Return negative if rowA < rowB,
     * positive if rowA > rowB, 0 if equal. When any column in the table defines this, the table
     * switches to the table's customSort mode; columns without a comparator fall back to standard
     * field-value comparison. Must not be used in lazy (server-side) mode.
     */
    sortComparator?: (rowA: T, rowB: T) => number;
}

export interface CellRendererParams<T> {
    data: T;
    col: ColumnDef<T>;
    /** Resolved value of `col.field` on this row. Supports dot-paths (e.g. `'owner.name'`). Type is `unknown` because nested paths yield arbitrary types. */
    value: unknown;
    rowIndex: number;
}

export type CellTemplateRef<T> = TemplateRef<{ $implicit: CellRendererParams<T> }>;

/**
 * The fully-resolved configuration for the table. All fields are required.
 * Callers provide a {@link TableViewOptions} partial; missing fields fall back to DEFAULT_TABLE_CONFIG.
 */
export interface TableConfig {
    /** Enable server-side lazy loading. Default: true */
    lazy: boolean;
    /** Show paginator controls. Default: true */
    paginated: boolean;
    /** Show striped rows. Default: false */
    striped: boolean;
    /** Row selection mode. Default: undefined (no selection). 'multiple' renders a leading checkbox column; 'single' uses row-click selection. */
    selectionMode: 'single' | 'multiple' | undefined;
    /** Field used as the unique row key for selection and virtual scroll. Default: 'id' */
    dataKey: string;
    /** Inline style object passed to the table. Default: { 'min-width': '50rem' } */
    tableStyle: Record<string, string>;
    /** Show the "X–Y of Z" current-page report below the table. Default: true */
    showCurrentPageReport: boolean;
    /** Number of rows per page. Default: 50 */
    pageSize: number;
    /** Rows-per-page options shown in the paginator dropdown. Pass undefined to hide the dropdown. Default: [10, 20, 50, 100, 200] */
    pageSizeOptions: number[] | undefined;
    /** Show the search filter in the table caption bar. Default: true */
    showSearch: boolean;
    /** Translation key for the search input placeholder. Default: 'artemisApp.course.exercise.search.searchPlaceholder' */
    searchPlaceholder: string;
    /** Translation key for the message shown when the table has no rows. Default: 'artemisApp.dataTable.search.noResults' */
    emptyMessageTranslation: string;
    /** Enable scrollable mode with fixed headers. Default: false */
    scrollable: boolean;
    /** Height of the scrollable data viewport. Only applies when scrollable is true. Accepts any CSS length value (e.g. '65vh', '400px'). Default: undefined */
    scrollHeight: string | undefined;
    /** Freeze the implicit row-actions column to the right edge. Requires `scrollable: true`. Default: false. */
    rowActionsFrozen: boolean;
    /** Logical alignment for the row-action cell. Default: end. */
    rowActionsAlignment: 'start' | 'end';
    /**
     * Fields used for the global search filter. When set, the table searches only these dot-path fields
     * instead of all column fields. Use this to include virtual fields (e.g. pre-computed search strings)
     * that have no corresponding visible column, or to exclude fields from search. Default: undefined
     * (the table falls back to all column `field` values).
     */
    globalFilterFields: string[] | undefined;
    /** Field to sort by on initial render. Works for both default and custom sort modes. Default: undefined (unsorted). */
    initialSortField: string | undefined;
    /** Initial sort direction. 1 = ascending, -1 = descending. Default: 1. */
    initialSortOrder: 1 | -1;
}

/**
 * Configuration options for the table-view component.
 * All fields are optional; omitted fields fall back to DEFAULT_TABLE_CONFIG.
 */
export interface TableViewOptions extends Partial<TableConfig> {
    hidePageSizeOptions?: boolean;
}

const DEFAULT_TABLE_CONFIG: TableConfig = {
    lazy: true,
    paginated: true,
    striped: false,
    selectionMode: undefined,
    dataKey: 'id',
    tableStyle: { 'min-width': '50rem' },
    showCurrentPageReport: true,
    pageSize: 50,
    pageSizeOptions: [10, 20, 50, 100, 200],
    showSearch: true,
    searchPlaceholder: 'artemisApp.course.exercise.search.searchPlaceholder',
    emptyMessageTranslation: 'artemisApp.dataTable.search.noResults',
    scrollable: false,
    scrollHeight: undefined,
    rowActionsFrozen: false,
    rowActionsAlignment: 'end',
    globalFilterFields: undefined,
    initialSortField: undefined,
    initialSortOrder: 1,
};

@Component({
    selector: 'jhi-table-view',
    imports: [
        NgClass,
        NgComponentOutlet,
        NgTemplateOutlet,
        FormsModule,
        TumAetUiTableDirective,
        TumAetUiCheckboxComponent,
        TumAetUiRadioButtonComponent,
        TumAetUiPaginatorComponent,
        TumAetUiProgressSpinnerComponent,
        TumAetUiInputDirective,
        TranslateDirective,
        ArtemisTranslatePipe,
        SearchFilterComponent,
        TumAetUiTooltipDirective,
        FaIconComponent,
    ],
    templateUrl: './table-view.html',
    styleUrl: './table-view.scss',
    encapsulation: ViewEncapsulation.None,
    host: { '[class.jhi-table-flex-scroll]': 'isFlexScroll()' },
})
export class TableViewComponent<T> {
    private static readonly SEARCH_DEBOUNCE_MS = 300;

    cols = input.required<ColumnDef<T>[]>();
    vals = input.required<T[]>();
    /**
     * Total number of records in the full dataset.
     * Must be provided in lazy/server-side mode so the paginator knows the full record count.
     * In non-lazy mode, falls back to `vals().length` when omitted.
     */
    totalRows = input<number | undefined>(undefined);
    rowActions = input<TemplateRef<{ $implicit: T }> | null>(null);
    /** Template rendered in the caption bar alongside the search field. Receives no context. */
    tableActions = input<TemplateRef<unknown> | null>(null);
    loading = input(false);
    options = input<TableViewOptions>({});
    /** Optional predicate controlling which rows can be selected. Rows returning true are visually disabled and cannot be selected. */
    isRowDisabled = input<((row: T) => boolean) | null>(null);
    /** Optional function returning a CSS class string (or ngClass-compatible object) applied to each row's `<tr>`. */
    rowClass = input<((row: T) => string) | null>(null);
    /** Tooltip text shown on the checkbox cell of disabled rows. Only applies when selectionMode is 'multiple'. */
    disabledRowTooltip = input<string | undefined>(undefined);
    /**
     * The parent's authoritative selection list. Restored into the table's local binding after each
     * data reload, because the table silently clears its selection when [value] changes.
     * Only used when selectionMode is 'multiple'.
     */
    selectionSource = input<T[]>([]);
    protected readonly selectionName = `table-selection-${nextTableSelectionId++}`;
    private readonly selectedRows = signal<T | T[] | undefined>(undefined);
    get tableSelection(): T | T[] | undefined {
        return this.selectedRows();
    }
    set tableSelection(value: T | T[] | undefined) {
        this.selectedRows.set(value);
    }

    private readonly columnHeaders = viewChildren<ElementRef<HTMLElement>>('columnHeader');
    private readonly selectionHeader = viewChild<ElementRef<HTMLElement>>('selectionHeader');
    private readonly actionsHeader = viewChild<ElementRef<HTMLElement>>('actionsHeader');
    private readonly measuredWidths = signal<number[]>([]);
    private readonly selectionWidth = signal(0);
    private readonly actionsWidth = signal(0);

    onLazyLoad = output<TableViewLazyLoadEvent>();
    onRowSelect = output<T | T[] | undefined>();
    /**
     * Emits the number of rows currently visible after the table applies a client-side filter (e.g. the global
     * search). Equals the full row count when no filter is active. Only meaningful in non-lazy mode; in lazy
     * mode filtering is delegated to the server, so prefer {@link totalRows}.
     */
    filteredRowsChange = output<number>();

    private readonly query = signal<{ field?: string; order: number; search: string }>({ order: 1, search: '' });
    private readonly columnFilters = signal<Record<string, string>>({});
    protected readonly faSort = faSort;
    protected readonly faSortUp = faSortUp;
    protected readonly faSortDown = faSortDown;
    protected readonly faHashtag = faHashtag;
    protected readonly faComment = faComment;
    protected readonly faCircleQuestion = faCircleQuestion;
    protected readonly sortField = computed(() => this.query().field ?? this.resolvedOptions().initialSortField);
    protected readonly sortOrder = computed(() => (this.query().field ? this.query().order : this.resolvedOptions().initialSortOrder));
    private searchFilter = viewChild(SearchFilterComponent);

    private debounceTimer: ReturnType<typeof setTimeout> | undefined;

    constructor() {
        inject(DestroyRef).onDestroy(() => clearTimeout(this.debounceTimer));
        afterNextRender(() => this.emitQuery());
        afterRenderEffect((onCleanup) => {
            const headers = this.columnHeaders();
            const selection = this.selectionHeader()?.nativeElement;
            const actions = this.actionsHeader()?.nativeElement;
            const observer = new ResizeObserver(() => {
                this.measuredWidths.set(headers.map((header) => header.nativeElement.getBoundingClientRect().width));
                this.selectionWidth.set(selection?.getBoundingClientRect().width ?? 0);
                this.actionsWidth.set(actions?.getBoundingClientRect().width ?? 0);
            });
            for (const header of headers) observer.observe(header.nativeElement);
            if (selection) observer.observe(selection);
            if (actions) observer.observe(actions);
            onCleanup(() => observer.disconnect());
        });
        effect(() => {
            const total = this.effectiveTotalRows();
            const size = Math.max(1, this.effectivePageSize());
            const lastFirst = total > 0 ? Math.floor((total - 1) / size) * size : 0;
            if (this.currentFirst() > lastFirst) {
                untracked(() => {
                    this.currentFirst.set(lastFirst);
                    this.emitQuery();
                });
            }
        });
        effect(() => {
            if (!this.resolvedOptions().lazy) {
                this.filteredRowsChange.emit(this.filteredValues().length);
            }
        });
        // Re-apply the caller-provided selection after each data reload.
        // the table silently clears tableSelection when [value] changes and selected items
        // are no longer present — this effect restores the correct checked state.
        effect(() => {
            const sel = this.selectionSource();
            if (this.resolvedOptions().selectionMode === 'multiple') {
                this.tableSelection = sel;
            }
        });
    }

    protected readonly currentFirst = signal(0);
    protected readonly Math = Math;
    /** Tracks user-driven page-size changes; undefined means use resolvedOptions().pageSize. */
    private readonly currentPageSizeOverride = signal<number | undefined>(undefined);

    /** Merges caller overrides onto defaults. Single source of truth for all the table config. */
    protected readonly resolvedOptions = computed<TableConfig>(() => {
        const opts = this.options();
        const tableConfig: TableConfig = {
            lazy: opts.lazy ?? DEFAULT_TABLE_CONFIG.lazy,
            paginated: opts.paginated ?? DEFAULT_TABLE_CONFIG.paginated,
            striped: opts.striped ?? DEFAULT_TABLE_CONFIG.striped,
            selectionMode: opts.selectionMode ?? DEFAULT_TABLE_CONFIG.selectionMode,
            dataKey: opts.dataKey ?? DEFAULT_TABLE_CONFIG.dataKey,
            tableStyle: opts.tableStyle ?? DEFAULT_TABLE_CONFIG.tableStyle,
            showCurrentPageReport: opts.showCurrentPageReport ?? DEFAULT_TABLE_CONFIG.showCurrentPageReport,
            pageSize: opts.pageSize ?? DEFAULT_TABLE_CONFIG.pageSize,
            pageSizeOptions: opts.hidePageSizeOptions ? undefined : (opts.pageSizeOptions ?? DEFAULT_TABLE_CONFIG.pageSizeOptions),
            showSearch: opts.showSearch ?? DEFAULT_TABLE_CONFIG.showSearch,
            searchPlaceholder: opts.searchPlaceholder ?? DEFAULT_TABLE_CONFIG.searchPlaceholder,
            emptyMessageTranslation: opts.emptyMessageTranslation ?? DEFAULT_TABLE_CONFIG.emptyMessageTranslation,
            scrollable: opts.scrollable ?? DEFAULT_TABLE_CONFIG.scrollable,
            scrollHeight: opts.scrollHeight ?? DEFAULT_TABLE_CONFIG.scrollHeight,
            rowActionsFrozen: opts.rowActionsFrozen ?? DEFAULT_TABLE_CONFIG.rowActionsFrozen,
            rowActionsAlignment: opts.rowActionsAlignment ?? DEFAULT_TABLE_CONFIG.rowActionsAlignment,
            globalFilterFields: opts.globalFilterFields ?? DEFAULT_TABLE_CONFIG.globalFilterFields,
            initialSortField: opts.initialSortField ?? DEFAULT_TABLE_CONFIG.initialSortField,
            initialSortOrder: opts.initialSortOrder ?? DEFAULT_TABLE_CONFIG.initialSortOrder,
        };
        return tableConfig;
    });

    /**
     * True when at least one column defines a custom sort comparator AND the table runs in client-side
     * (non-lazy) mode. Switches the entire the table into customSort mode so (sortFunction) fires for every
     * column click; columns without a comparator are handled by the default field-value fallback in
     * onCustomSort(). Always false in lazy mode — sorting is delegated to the server there — which
     * structurally enforces the "sortComparator must not be used in lazy mode" contract documented on
     * {@link ColumnDef.sortComparator}.
     */
    protected readonly hasCustomSort = computed(() => !this.resolvedOptions().lazy && this.cols().some((c) => !!c.sortComparator));

    /** Falls back to vals().length in non-lazy mode. */
    protected readonly effectiveTotalRows = computed(() => (this.resolvedOptions().lazy ? (this.totalRows() ?? this.vals().length) : this.filteredValues().length));

    protected readonly showCaption = computed(() => this.resolvedOptions().showSearch || !!this.tableActions());
    protected readonly isFlexScroll = computed(() => this.resolvedOptions().scrollable && this.resolvedOptions().scrollHeight === 'flex');

    protected readonly emptyMessageColSpan = computed(() => this.cols().length + (this.rowActions() ? 1 : 0) + (this.resolvedOptions().selectionMode ? 1 : 0));

    /** Uses the caller-set pageSize until the user changes it via the paginator. */
    protected readonly effectivePageSize = computed(() => this.currentPageSizeOverride() ?? this.resolvedOptions().pageSize);

    protected readonly itemRangeBegin = computed(() => (this.effectiveTotalRows() === 0 ? 0 : Math.min(this.effectiveTotalRows(), this.currentFirst() + 1)));
    protected readonly itemRangeEnd = computed(() => Math.min(this.effectiveTotalRows(), this.currentFirst() + this.effectivePageSize()));

    /** Pre-built renderer params for every cell, keyed by row data object. Recomputed only when vals() or cols() change. */
    protected readonly renderedRows = computed(() => {
        const vals = this.vals();
        const cols = this.cols();
        const map = new Map<T, CellRendererParams<T>[]>();
        for (const [rowIndex, data] of vals.entries()) {
            map.set(
                data,
                cols.map((col) => this.buildRendererParams(data, col, rowIndex)),
            );
        }
        return map;
    });

    protected readonly hasSelectableRows = computed(() => this.visibleValues().some((row) => !this.isRowDisabled()?.(row)));

    protected readonly rowSelectable = (event: { data: T; index: number }) => !this.isRowDisabled()?.(event.data);

    onGlobalSearch(event: string): void {
        clearTimeout(this.debounceTimer);
        this.debounceTimer = setTimeout(() => {
            this.query.update((query) => cloneWith(query, { search: event.trim().toLowerCase() }));
            this.currentFirst.set(0);
            this.emitQuery();
        }, TableViewComponent.SEARCH_DEBOUNCE_MS);
    }

    protected readonly filteredValues = computed(() => {
        if (this.resolvedOptions().lazy) return this.vals();
        const search = this.query().search;
        const fields = this.resolvedOptions().globalFilterFields ?? this.cols().flatMap((col) => (col.field ? [col.field] : []));
        const filters = this.columnFilters();
        const rows = this.vals().filter((row) => {
            if (
                search &&
                !fields.some((field) =>
                    String(get(row, field) ?? '')
                        .toLowerCase()
                        .includes(search),
                )
            )
                return false;
            return Object.entries(filters).every(([field, value]) => {
                if (!value) return true;
                const cell = get(row, field);
                const type = this.cols().find((col) => col.field === field)?.filterType;
                if (type === 'numeric') return Number(cell) === Number(value);
                if (type === 'boolean') return String(cell) === value;
                if (type === 'date') return String(cell ?? '').slice(0, 10) === value;
                return String(cell ?? '')
                    .toLowerCase()
                    .includes(value.toLowerCase());
            });
        });
        this.onCustomSort({ data: rows, field: this.sortField(), order: this.sortOrder() });
        return rows;
    });

    protected readonly visibleValues = computed(() => {
        const rows = this.filteredValues();
        return this.resolvedOptions().lazy || !this.resolvedOptions().paginated ? rows : rows.slice(this.currentFirst(), this.currentFirst() + this.effectivePageSize());
    });

    protected sortColumn(field: string): void {
        const order = this.sortField() === field ? -this.sortOrder() : 1;
        this.query.update((query) => cloneWith(query, { field, order }));
        this.currentFirst.set(0);
        this.emitQuery();
    }

    protected filterColumn(field: string, value: string): void {
        this.columnFilters.update((filters) => cloneWith(filters, { [field]: value }));
        this.currentFirst.set(0);
        this.emitQuery();
    }

    protected filterColumnInput(field: string, event: Event): void {
        this.filterColumn(field, (event.target as HTMLInputElement | HTMLSelectElement).value);
    }

    protected frozenOffset(index: number, side: 'left' | 'right'): string | undefined {
        const columns = this.cols();
        const col = columns[index];
        if (!col.frozen || (col.alignFrozen ?? 'left') !== side) return undefined;
        const widths: string[] = [];
        columns.forEach((column, otherIndex) => {
            if (column.frozen && (column.alignFrozen ?? 'left') === side && (side === 'left' ? otherIndex < index : otherIndex > index)) {
                widths.push(this.measuredWidths()[otherIndex] ? `${this.measuredWidths()[otherIndex]}px` : (column.width ?? '0px'));
            }
        });
        if (side === 'left' && this.resolvedOptions().selectionMode) widths.push(`${this.selectionWidth()}px`);
        if (side === 'right' && this.rowActions() && this.resolvedOptions().rowActionsFrozen) widths.push(`${this.actionsWidth()}px`);
        return widths.length ? `calc(${widths.join(' + ')})` : '0px';
    }

    private rowsMatch(a: T, b: T): boolean {
        const key = this.resolvedOptions().dataKey;
        const id = get(a, key);
        return Object.is(a, b) || (id !== undefined && id === get(b, key));
    }

    protected selected(row: T): boolean {
        const selection = this.tableSelection;
        const same = (other: T) => this.rowsMatch(other, row);
        return Array.isArray(selection) ? selection.some(same) : selection !== undefined && same(selection);
    }

    protected toggleRow(row: T, checked: boolean): void {
        if (this.isRowDisabled()?.(row)) return;
        if (this.resolvedOptions().selectionMode === 'single') {
            this.tableSelection = checked ? row : undefined;
        } else {
            const selection = Array.isArray(this.tableSelection) ? this.tableSelection : [];
            this.tableSelection = checked ? (this.selected(row) ? selection : [...selection, row]) : selection.filter((other) => !this.rowsMatch(other, row));
        }
        this.handleRowSelectChange();
    }

    protected toggleAll(checked: boolean): void {
        const rows = this.visibleValues().filter((row) => !this.isRowDisabled()?.(row));
        const selection = Array.isArray(this.tableSelection) ? this.tableSelection : [];
        this.tableSelection = checked
            ? [...selection, ...rows.filter((row) => !this.selected(row))]
            : selection.filter((selected) => !rows.some((row) => this.rowsMatch(row, selected)));
        this.handleRowSelectChange();
    }

    protected allSelected(): boolean {
        const rows = this.visibleValues().filter((row) => !this.isRowDisabled()?.(row));
        return rows.length > 0 && rows.every((row) => this.selected(row));
    }

    protected someSelected(): boolean {
        return this.visibleValues().some((row) => this.selected(row)) && !this.allSelected();
    }

    protected goToPage(page: number): void {
        this.pageChange({ first: page * this.effectivePageSize(), rows: this.effectivePageSize() });
        this.emitQuery();
    }

    protected changePageSize(rows: number): void {
        this.pageChange({ first: 0, rows });
        this.emitQuery();
    }

    private emitQuery(): void {
        if (!this.resolvedOptions().lazy) return;
        const filters: NonNullable<TableViewLazyLoadEvent['filters']> = {};
        for (const [field, value] of Object.entries(this.columnFilters())) {
            if (value) filters[field] = { value, matchMode: 'contains' };
        }
        const search = this.query().search;
        if (search) filters.global = { value: search, matchMode: 'contains' };
        this.handleLazyLoad({
            first: this.currentFirst(),
            rows: this.effectivePageSize(),
            sortField: this.sortField(),
            sortOrder: this.sortOrder(),
            filters,
            globalFilter: search || null,
        });
    }

    buildRendererParams(data: T, col: ColumnDef<T>, rowIndex: number): CellRendererParams<T> {
        const params: CellRendererParams<T> = {
            data,
            col,
            value: col?.field ? get(data, col.field) : undefined,
            rowIndex,
        };
        return params;
    }

    /**
     * Called by the table when customSort is active (i.e. at least one column has a sortComparator).
     * Sorts event.data in place — the table re-renders from the mutated array.
     * Columns with a sortComparator use it; all others fall back to standard field-value comparison.
     */
    onCustomSort(event: { data: T[]; field?: string; order?: number }): void {
        if (!event.data) return;
        const col = this.cols().find((c) => c.field === event.field);
        const comparator = col?.sortComparator;
        const order = event.order ?? 1;
        event.data.sort((rowA: T, rowB: T) => {
            if (comparator) {
                return order * comparator(rowA, rowB);
            }
            // Fallback: standard value sort for columns without a custom comparator
            const val1 = col?.field ? get(rowA, col.field) : undefined;
            const val2 = col?.field ? get(rowB, col.field) : undefined;
            if (val1 == null && val2 == null) return 0;
            if (val1 == null) return order;
            if (val2 == null) return -order;
            return typeof val1 === 'string' && typeof val2 === 'string' ? order * val1.localeCompare(val2) : order * (val1 < val2 ? -1 : val1 > val2 ? 1 : 0);
        });
    }

    handleLazyLoad(event: TableViewLazyLoadEvent): void {
        this.currentFirst.set(event.first ?? 0);
        this.onLazyLoad.emit(event);
    }

    handleRowSelectChange(): void {
        this.onRowSelect.emit(this.tableSelection);
    }

    /**
     * Fired by the table whenever a client-side filter is applied. Emits the resulting visible row count
     * (the table sets filteredValue to null when the filter is cleared, in which case all rows are shown).
     */
    onFilter(event: { filteredValue?: T[] | null }): void {
        this.filteredRowsChange.emit(event.filteredValue?.length ?? this.vals().length);
    }

    pageChange(event: TableViewPageEvent): void {
        this.currentPageSizeOverride.set(event.rows);
        this.currentFirst.set(event.first);
    }

    /**
     * Clears the search field and all pagination state, then fires an immediate lazy load at page 0.
     * Use this when the underlying data has changed and you want a clean slate (e.g. after registering a student).
     * No-op in non-lazy mode.
     */
    reset(): void {
        if (!this.resolvedOptions().lazy) return;
        this.currentFirst.set(0);
        this.currentPageSizeOverride.set(undefined);
        this.searchFilter()?.resetSearchValue();
        clearTimeout(this.debounceTimer);
        this.query.update((query) => cloneWith(query, { search: '' }));
        this.columnFilters.set({});
        this.emitQuery();
    }

    /** Reload a page in place while preserving the user's page size, sort and filters. */
    reload(page = this.currentPageNumber()): void {
        if (!this.resolvedOptions().lazy) return;
        this.currentFirst.set(page * this.effectivePageSize());
        this.emitQuery();
    }

    /**
     * Reload after removing `removedCount` rows from the dataset, staying on the current page unless that page
     * is no longer valid (e.g. the removed row was the last one on the last page), in which case it steps back
     * to the last valid page. Uses only the table's own last-known total, so it always resolves in a single
     * request — unlike {@link reload}, it never needs to fetch a page first to discover it came back empty.
     * No-op in non-lazy mode.
     */
    reloadAfterRemoval(removedCount = 1): void {
        if (!this.resolvedOptions().lazy) return;
        const newTotal = Math.max(0, this.effectiveTotalRows() - removedCount);
        const lastValidPage = Math.max(0, Math.ceil(newTotal / this.effectivePageSize()) - 1);
        this.reload(Math.min(this.currentPageNumber(), lastValidPage));
    }

    private currentPageNumber(): number {
        return Math.floor(this.currentFirst() / this.effectivePageSize());
    }

    /** Clears the current row selection. */
    clearSelection(): void {
        this.tableSelection = this.resolvedOptions().selectionMode === 'multiple' ? [] : undefined;
    }
}
