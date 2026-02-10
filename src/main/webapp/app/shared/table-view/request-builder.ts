export type SortDir = 'asc' | 'desc';

export interface DbQuery {
    page: number; // 0-based
    size: number;
    sort: string; // e.g. "name,asc"
    filters: Record<string, unknown>; // backend-specific payload (generic)
    globalFilter?: string;
}

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

function sortDirFromOrder(order?: 1 | -1 | 0 | null): SortDir | null {
    if (order === 1) return 'asc';
    if (order === -1) return 'desc';
    return null;
}

function normalizeSort(e: LazyLoadLike): string {
    const field = e.sortField?.trim() ?? 'id';
    const dir = sortDirFromOrder(e.sortOrder);
    return field && dir ? `${field},${dir}` : '';
}

/**
 * Converts PrimeNG LazyLoad event -> generic DbQuery
 * - page is 0-based
 * - sort is ["field,asc"] etc
 * - filters keeps a generic normalized structure you can later map to your backend format
 */
export function buildDbQueryFromLazyEvent(e: LazyLoadLike, defaults: { page?: number; size?: number } = {}): DbQuery {
    const size = e.rows ?? defaults.size ?? 20;
    const first = e.first ?? 0;
    const page = size > 0 ? Math.floor(first / size) : (defaults.page ?? 0);

    const sort = normalizeSort(e);

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

    const globalFilter = e.globalFilter !== undefined && e.globalFilter !== null && `${e.globalFilter}`.trim() !== '' ? `${e.globalFilter}`.trim() : undefined;

    return { page, size, sort, filters, globalFilter };
}
