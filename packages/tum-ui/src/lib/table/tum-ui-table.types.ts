import { TemplateRef } from '@angular/core';

export interface ColumnDef<T> {
    /** Top-level property or lodash-compatible nested property path. */
    field?: (keyof T & string) | (string & {});
    header?: string;
    headerKey?: string;
    width?: string;
    sort?: boolean;
    hideBelow?: 'sm' | 'md' | 'lg' | 'xl' | '2xl';
    templateRef?: CellTemplateRef<T>;
}

export interface CellRendererParams<T> {
    data: T;
    col: ColumnDef<T>;

    value: unknown;
    rowIndex: number;
}

export type CellTemplateRef<T> = TemplateRef<{ $implicit: CellRendererParams<T> }>;

export type TumUiSortDirection = 'asc' | 'desc';

export interface TumUiSortState {
    field: string;
    direction: TumUiSortDirection;
}

export interface TumUiTableQueryEvent {
    /** Zero-based page index. */
    page: number;
    pageSize: number;
    sort?: TumUiSortState;
    searchTerm?: string;
}
