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

export type TumUiSortOrder = 1 | -1;

export interface TumUiSortState {
    field: string;
    order: TumUiSortOrder;
}

/**
 * Lazy-load event emitted by {@link TumUiTableComponent} on sort / page / search changes.
 * Structurally compatible with the query shape the server pagination expects (see the request builder).
 */
export interface TumUiTableLazyEvent {
    first: number;
    rows: number;
    sortField?: string;
    sortOrder?: TumUiSortOrder;
    globalFilter?: string;
}
