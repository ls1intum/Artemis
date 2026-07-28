import { SearchTermPageableSearch, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { TumUiTableQueryEvent } from '@tumaet/ui-angular';

/**
 * Converts a {@link TumUiTableQueryEvent} into a {@link SearchTermPageableSearch}.
 * Page numbers are 0-based (the event already carries a 0-based `page`, matching this endpoint family);
 * falls back to sensible defaults when event fields are absent. Owned, PrimeNG-free counterpart of the
 * legacy table-view request builder.
 */
export function buildDbQueryFromTableEvent(event: TumUiTableQueryEvent, defaults: { pageSize?: number } = {}): SearchTermPageableSearch {
    return {
        page: event.page,
        pageSize: event.pageSize || defaults.pageSize || 50,
        sortedColumn: event.sort?.field.trim() || 'id',
        sortingOrder: event.sort?.direction === 'desc' ? SortingOrder.DESCENDING : SortingOrder.ASCENDING,
        searchTerm: event.searchTerm?.trim() ?? '',
    };
}
