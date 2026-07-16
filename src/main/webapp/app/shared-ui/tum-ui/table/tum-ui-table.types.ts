import { TemplateRef } from '@angular/core';

/**
 * Column definition for {@link TumUiTableComponent}, part of the tum-aet-ui kit.
 * A trimmed, PrimeNG-free copy of the legacy table-view ColumnDef (only the fields the owned table uses).
 */
export interface ColumnDef<T> {
    /** Top-level key of T (`'name'`) or dot-path to a nested field (`'owner.name'`), resolved via lodash `get`. */
    field?: string;
    header?: string;
    headerKey?: string;
    width?: string;
    sort?: boolean;
    /** Render the cell with a parent-defined template that receives {@link CellRendererParams} as `$implicit`. */
    templateRef?: CellTemplateRef<T>;
}

export interface CellRendererParams<T> {
    data: T;
    col: ColumnDef<T>;
    /** Resolved value of `col.field` on this row (dot-paths supported). `unknown` because nested paths yield arbitrary types. */
    value: unknown;
    rowIndex: number;
}

export type CellTemplateRef<T> = TemplateRef<{ $implicit: CellRendererParams<T> }>;

/** Sort direction of a column: ascending or descending. */
export type TumUiSortDirection = 'asc' | 'desc';

export interface TumUiSortState {
    field: string;
    direction: TumUiSortDirection;
}

/**
 * Emitted by {@link TumUiTableComponent} whenever the query changes (sort / page / search) so the
 * consumer can (re)fetch the matching rows. Field names are intent-revealing (not PrimeNG-derived);
 * feed the event into `buildDbQueryFromTableEvent` to get the standard Artemis paged-search shape.
 */
export interface TumUiTableQueryEvent {
    /** Zero-based index of the first row to load (page offset). */
    offset: number;
    /** Number of rows per page. */
    pageSize: number;
    /** Column to sort by, or undefined when unsorted. */
    sortField?: string;
    /** Sort direction for {@link sortField}. */
    sortDirection?: TumUiSortDirection;
    /** Trimmed global search term, or undefined when empty. */
    globalFilter?: string;
}
