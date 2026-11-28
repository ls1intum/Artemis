/** Query emitted by the application table wrapper. Offsets and page sizes are zero based. */
export interface TableViewLazyLoadEvent {
    first?: number;
    last?: number;
    rows?: number | null;
    sortField?: string | string[] | null;
    sortOrder?: number | null;
    globalFilter?: string | string[] | null;
    filters?: Record<string, TableViewFilter | TableViewFilter[] | undefined>;
    multiSortMeta?: { field: string; order: number }[] | null;
}

export interface TableViewFilter {
    value?: unknown;
    matchMode?: string;
    operator?: string;
}

export interface TableViewPageEvent {
    first: number;
    rows: number;
}
