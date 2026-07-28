import { SearchTermPageableSearch, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { TumUiTableQueryEvent } from '@tumaet/ui-angular';

export function buildDbQueryFromTableEvent(event: TumUiTableQueryEvent, defaults: { pageSize?: number } = {}): SearchTermPageableSearch {
    return {
        page: event.page,
        pageSize: event.pageSize || defaults.pageSize || 50,
        sortedColumn: event.sort?.field.trim() || 'id',
        sortingOrder: event.sort?.direction === 'desc' ? SortingOrder.DESCENDING : SortingOrder.ASCENDING,
        searchTerm: event.searchTerm?.trim() ?? '',
    };
}
