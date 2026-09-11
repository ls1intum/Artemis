import { TemplateRef } from '@angular/core';

export interface ColumnDef<T> {
    /** Top-level property or nested path such as `owner.name` or `items[0].label`. */
    field?: (keyof T & string) | (string & {});
    header?: string;
    headerKey?: string;
    /** Translation key for a hint explaining a column whose heading alone is ambiguous, shown behind a help icon. */
    headerTooltip?: string;
    /** Minimum width as any CSS length. Prefer `rem` so a column sized to hold text grows with the reader's font. */
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
    pageIndex: number;
    pageSize: number;
    sort?: TumUiSortState;
    searchTerm?: string;
}
