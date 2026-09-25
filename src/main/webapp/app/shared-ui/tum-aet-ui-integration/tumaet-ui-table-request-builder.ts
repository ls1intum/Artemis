import { SearchTermPageableSearch, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { TumAetUiTableQueryEvent } from '@tumaet/ui-angular';

export function buildDbQueryFromTableEvent(event: TumAetUiTableQueryEvent, defaults: { pageSize?: number } = {}): SearchTermPageableSearch {
    return {
        page: event.pageIndex,
        pageSize: event.pageSize || defaults.pageSize || 50,
        sortedColumn: event.sort?.field.trim() || 'id',
        sortingOrder: event.sort?.direction === 'desc' ? SortingOrder.DESCENDING : SortingOrder.ASCENDING,
        searchTerm: event.searchTerm?.trim() ?? '',
    };
}
