import { NgComponentOutlet, NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, TemplateRef, Type, ViewEncapsulation, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { Tooltip } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { Table, TableLazyLoadEvent, TableModule, TablePageEvent } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';

export interface ColumnDef<T> {
    field?: keyof T;
    header?: string;
    headerKey?: string;
    /** PrimeIcons CSS class string, e.g. 'pi pi-hashtag'. Rendered as <i> in the column header. */
    headerIcon?: string;
    /** Translation key shown as a PrimeNG tooltip on the column header. */
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
}

export interface CellRendererParams<T> {
    data: T;
    col: ColumnDef<T>;
    value: T[keyof T] | undefined;
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
    /** Inline style object passed to p-table. Default: { 'min-width': '50rem' } */
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
};

@Component({
    selector: 'jhi-table-view',
    imports: [NgComponentOutlet, NgTemplateOutlet, FormsModule, TableModule, TranslateDirective, ArtemisTranslatePipe, SearchFilterComponent, Tooltip],
    templateUrl: './table-view.html',
    styleUrl: './table-view.scss',
    encapsulation: ViewEncapsulation.None,
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
    /** Tooltip text shown on the checkbox cell of disabled rows. Only applies when selectionMode is 'multiple'. */
    disabledRowTooltip = input<string | undefined>(undefined);
    /**
     * The parent's authoritative selection list. Restored into PrimeNG's local binding after each
     * data reload, because PrimeNG silently clears its selection when [value] changes.
     * Only used when selectionMode is 'multiple'.
     */
    selectionSource = input<T[]>([]);
    tableSelection: T | T[] | undefined;

    onLazyLoad = output<TableLazyLoadEvent>();
    onRowSelect = output<T | T[] | undefined>();

    dt = viewChild.required<Table>('dt');
    private searchFilter = viewChild(SearchFilterComponent);

    private debounceTimer: ReturnType<typeof setTimeout> | undefined;

    constructor() {
        inject(DestroyRef).onDestroy(() => clearTimeout(this.debounceTimer));
        // Re-apply the caller-provided selection after each data reload.
        // PrimeNG silently clears tableSelection when [value] changes and selected items
        // are no longer present — this effect restores the correct checked state.
        effect(() => {
            const sel = this.selectionSource();
            if (this.resolvedOptions().selectionMode === 'multiple') {
                this.tableSelection = sel;
            }
        });
    }

    private readonly currentFirst = signal(0);
    /** Tracks user-driven page-size changes; undefined means use resolvedOptions().pageSize. */
    private readonly currentPageSizeOverride = signal<number | undefined>(undefined);

    /** Merges caller overrides onto defaults. Single source of truth for all p-table config. */
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
        };
        return tableConfig;
    });

    /** Falls back to vals().length in non-lazy mode. */
    protected readonly effectiveTotalRows = computed(() => this.totalRows() ?? this.vals().length);

    protected readonly showCaption = computed(() => this.resolvedOptions().showSearch || !!this.tableActions());

    protected readonly emptyMessageColSpan = computed(() => this.cols().length + (this.rowActions() ? 1 : 0) + (this.resolvedOptions().selectionMode === 'multiple' ? 1 : 0));

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

    protected readonly hasSelectableRows = computed(() => this.vals().some((row) => !this.isRowDisabled()?.(row)));

    protected readonly rowSelectable = (event: { data: T; index: number }) => !this.isRowDisabled()?.(event.data);

    onGlobalSearch(event: string): void {
        const value = event.trim().toLowerCase();

        clearTimeout(this.debounceTimer);
        this.debounceTimer = setTimeout(() => {
            this.dt().first = 0;
            this.dt().filterGlobal(value, 'contains');
        }, TableViewComponent.SEARCH_DEBOUNCE_MS);
    }

    buildRendererParams(data: T, col: ColumnDef<T>, rowIndex: number): CellRendererParams<T> {
        const params: CellRendererParams<T> = {
            data,
            col,
            value: col?.field && (col.field satisfies keyof T) ? data?.[col.field] : undefined,
            rowIndex,
        };
        return params;
    }

    handleLazyLoad(event: TableLazyLoadEvent): void {
        this.currentFirst.set(event.first ?? 0);
        this.onLazyLoad.emit(event);
    }

    handleRowSelectChange(): void {
        this.onRowSelect.emit(this.tableSelection);
    }

    pageChange(event: TablePageEvent): void {
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
        // resetSearchValue() synchronously emits newSearchEvent('') → onGlobalSearch('') → queues a debounce.
        // We cancel that debounce immediately so only the direct handleLazyLoad below fires.
        this.searchFilter()?.resetSearchValue();
        clearTimeout(this.debounceTimer);
        const dt = this.dt();
        dt.first = 0;
        dt.filters = {};
        this.handleLazyLoad({
            first: 0,
            rows: this.effectivePageSize(),
            sortField: dt.sortField as string | undefined,
            sortOrder: dt.sortOrder ?? undefined,
            filters: {},
            globalFilter: null,
            multiSortMeta: undefined,
        });
    }

    /**
     * Goes back to page 0 and fires an immediate lazy load, preserving the current search term and sort.
     * Use this when an external state change requires a fresh page 1 without disturbing the user's search
     * (e.g. the search term in a parent component changed).
     * No-op in non-lazy mode.
     */
    reload(): void {
        if (!this.resolvedOptions().lazy) return;
        this.currentFirst.set(0);
        this.currentPageSizeOverride.set(undefined);
        const dt = this.dt();
        dt.first = 0;
        this.handleLazyLoad({
            first: 0,
            rows: this.effectivePageSize(),
            sortField: dt.sortField as string | undefined,
            sortOrder: dt.sortOrder ?? undefined,
            filters: dt.filters ?? {},
            globalFilter: (dt.filters?.['global'] as { value?: string } | undefined)?.value ?? null,
            multiSortMeta: undefined,
        });
    }

    /** Clears the current row selection. */
    clearSelection(): void {
        this.tableSelection = this.resolvedOptions().selectionMode === 'multiple' ? [] : undefined;
    }
}
