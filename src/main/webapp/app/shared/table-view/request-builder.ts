import { TableLazyLoadEvent } from 'primeng/table';
import { SearchTermPageableSearch, SortingOrder } from '../table/pageable-table';

function getSortingOrder(order: number | undefined | null): SortingOrder {
    return order === -1 ? SortingOrder.DESCENDING : SortingOrder.ASCENDING;
}

function getSortedColumn(sortField: string | string[] | undefined | null): string {
    const field = Array.isArray(sortField) ? sortField[0] : sortField;
    return field?.trim() || 'id';
}

function getSearchTerm(globalFilter: string | string[] | undefined | null): string {
    const raw = Array.isArray(globalFilter) ? globalFilter[0] : globalFilter;
    return raw?.trim() ?? '';
}

/**
 * Converts a PrimeNG {@link TableLazyLoadEvent} into a {@link SearchTermPageableSearch}.
 * Page numbers are 0-based; falls back to sensible defaults when event fields are absent.
 */
export function buildDbQueryFromLazyEvent(event: TableLazyLoadEvent, defaults: { page?: number; pageSize?: number } = {}): SearchTermPageableSearch {
    const pageSize = event.rows ?? defaults.pageSize ?? 50;
    const first = event.first ?? 0;
    const page = pageSize > 0 ? Math.floor(first / pageSize) : (defaults.page ?? 0);

    return {
        page,
        pageSize,
        sortedColumn: getSortedColumn(event.sortField),
        sortingOrder: getSortingOrder(event.sortOrder),
        searchTerm: getSearchTerm(event.globalFilter),
    };
}
