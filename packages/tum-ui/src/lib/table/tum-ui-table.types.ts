import { TemplateRef } from '@angular/core';

export interface ColumnDef<T> {
    /** Top-level property or nested path such as `owner.name` or `items[0].label`. */
    field?: (keyof T & string) | (string & {});
    header?: string;
    headerKey?: string;
    /** Translation key for a hint explaining a column whose heading alone is ambiguous, shown behind a help icon. */
    headerTooltip?: string;
    /**
     * Minimum width as any CSS length. Prefer `rem` so a column sized to hold text grows with the reader's font.
     *
     * This is a floor, never a cap: the column claims the width whether or not its cells fill it, and the table can
     * only take the space back from a column that declares none. Set it where a cell would otherwise collapse to
     * nothing, not as a guess at how wide the content wants to be — auto layout already sizes a column to its content
     * and shares out whatever is left. Floors summing past the available width push the table into its scroller.
     */
    width?: string;
    sort?: boolean;
    /**
     * Lets a long heading wrap onto a second line. Headings are nowrap by default, which makes each one a floor its
     * column can never go below, so a heading much wider than the values under it is worth wrapping instead.
     */
    wrapHeader?: boolean;
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
