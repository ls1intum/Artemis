import { SearchTermPageableSearch, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { TumUiTableLazyEvent } from 'app/shared-ui/tum-ui/table/tum-ui-table.types';

/**
 * Converts a {@link TumUiTableLazyEvent} into a {@link SearchTermPageableSearch}.
 * Page numbers are 0-based; falls back to sensible defaults when event fields are absent.
 * Owned, PrimeNG-free counterpart of the legacy table-view request builder.
 */
export function buildDbQueryFromLazyEvent(event: TumUiTableLazyEvent, defaults: { page?: number; pageSize?: number } = {}): SearchTermPageableSearch {
    const pageSize = event.rows || defaults.pageSize || 50;
    const first = event.first ?? 0;
    const page = pageSize > 0 ? Math.floor(first / pageSize) : (defaults.page ?? 0);

    return {
        page,
        pageSize,
        sortedColumn: event.sortField?.trim() || 'id',
        sortingOrder: event.sortOrder === -1 ? SortingOrder.DESCENDING : SortingOrder.ASCENDING,
        searchTerm: event.globalFilter?.trim() ?? '',
    };
}
