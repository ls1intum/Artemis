import { SearchTermPageableSearch, SortingOrder } from '../table/pageable-table';

// Minimal shape so you don't depend on PrimeNG types directly
type LazyLoadLike = {
    first?: number;
    rows?: number;
    sortField?: string;
    sortOrder?: 1 | -1 | 0 | null;
    multiSortMeta?: Array<{ field?: string; order?: 1 | -1 | 0 | null }>;
    filters?: Record<string, { value?: any; matchMode?: string; operator?: string; constraints?: any[] } | Array<{ value?: any; matchMode?: string }> | undefined>;
    globalFilter?: any;
};

function getSortingOrder(order?: 1 | -1 | 0 | null): SortingOrder {
    if (order === 1) return SortingOrder.ASCENDING;
    if (order === -1) return SortingOrder.DESCENDING;
    return SortingOrder.ASCENDING;
}

function getSortedColumn(e: LazyLoadLike): string {
    const field = e.sortField?.trim() ?? 'id';
    return field;
}

/**
 * Converts PrimeNG LazyLoad event -> generic DbQuery
 * - page is 0-based
 * - sort is ["field,asc"] etc
 * - filters keeps a generic normalized structure you can later map to your backend format
 */
export function buildDbQueryFromLazyEvent(e: LazyLoadLike, defaults: { page?: number; size?: number } = {}): SearchTermPageableSearch {
    const pageSize = e.rows ?? defaults.size ?? 20;
    const first = e.first ?? 0;
    const page = pageSize > 0 ? Math.floor(first / pageSize) : (defaults.page ?? 0);

    const sortedColumn = getSortedColumn(e);
    const sortingOrder = getSortingOrder(e.sortOrder);

    // Normalize PrimeNG filters into a simple structure:
    // filters[field] = { value, matchMode, operator, constraints? }
    const filters: Record<string, unknown> = {};
    const rawFilters = e.filters ?? {};

    for (const [field, f] of Object.entries(rawFilters)) {
        if (!f) continue;

        // PrimeNG sometimes provides array form, sometimes object form with constraints
        // We keep it generic and compact.
        if (Array.isArray(f)) {
            const firstRule = f[0];
            if (firstRule?.value !== undefined && firstRule?.value !== null && firstRule?.value !== '') {
                filters[field] = {
                    value: firstRule.value,
                    matchMode: firstRule.matchMode,
                };
            }
            continue;
        }

        // object form
        const value = (f as any).value;
        const constraints = (f as any).constraints;
        const matchMode = (f as any).matchMode;
        const operator = (f as any).operator;

        // If constraints exist (AND/OR rules), keep them
        if (Array.isArray(constraints) && constraints.length > 0) {
            const cleaned = constraints
                .filter((c: any) => c?.value !== undefined && c?.value !== null && c?.value !== '')
                .map((c: any) => ({ value: c.value, matchMode: c.matchMode }));

            if (cleaned.length > 0) {
                filters[field] = { operator, constraints: cleaned };
            }
            continue;
        }

        // simple single value
        if (value !== undefined && value !== null && value !== '') {
            filters[field] = { value, matchMode, operator };
        }
    }

    const searchTerm = e.globalFilter !== undefined && e.globalFilter !== null && `${e.globalFilter}`.trim() !== '' ? `${e.globalFilter}`.trim() : '';

    return { page, pageSize, sortedColumn, sortingOrder, searchTerm };
}
