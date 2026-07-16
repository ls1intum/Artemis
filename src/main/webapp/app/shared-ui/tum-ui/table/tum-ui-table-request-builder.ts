import { SearchTermPageableSearch, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { TumUiTableQueryEvent } from 'app/shared-ui/tum-ui/table/tum-ui-table.types';

/**
 * Converts a {@link TumUiTableQueryEvent} into a {@link SearchTermPageableSearch}.
 * Page numbers are 0-based; falls back to sensible defaults when event fields are absent.
 * Owned, PrimeNG-free counterpart of the legacy table-view request builder.
 */
export function buildDbQueryFromTableEvent(event: TumUiTableQueryEvent, defaults: { page?: number; pageSize?: number } = {}): SearchTermPageableSearch {
    const pageSize = event.pageSize || defaults.pageSize || 50;
    const offset = event.offset ?? 0;
    const page = pageSize > 0 ? Math.floor(offset / pageSize) : (defaults.page ?? 0);

    return {
        page,
        pageSize,
        sortedColumn: event.sortField?.trim() || 'id',
        sortingOrder: event.sortDirection === 'desc' ? SortingOrder.DESCENDING : SortingOrder.ASCENDING,
        searchTerm: event.globalFilter?.trim() ?? '',
    };
}
