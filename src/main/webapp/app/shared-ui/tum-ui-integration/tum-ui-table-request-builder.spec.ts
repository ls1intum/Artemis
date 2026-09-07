import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { TumUiTableQueryEvent } from '@tumaet/ui-angular';
import { buildDbQueryFromTableEvent } from './tum-ui-table-request-builder';

function event(overrides: Partial<TumUiTableQueryEvent> = {}): TumUiTableQueryEvent {
    return { pageIndex: 0, pageSize: 50, ...overrides };
}

describe('buildDbQueryFromTableEvent', () => {
    it('maps a complete table event', () => {
        expect(buildDbQueryFromTableEvent(event({ pageIndex: 4, pageSize: 20, sort: { field: 'name', direction: 'desc' }, searchTerm: '  alice  ' }))).toEqual({
            page: 4,
            pageSize: 20,
            sortedColumn: 'name',
            sortingOrder: SortingOrder.DESCENDING,
            searchTerm: 'alice',
        });
    });

    it('normalizes an incomplete table event', () => {
        expect(buildDbQueryFromTableEvent(event({ pageSize: 0, sort: { field: '   ', direction: 'asc' } }))).toEqual({
            page: 0,
            pageSize: 50,
            sortedColumn: 'id',
            sortingOrder: SortingOrder.ASCENDING,
            searchTerm: '',
        });
    });

    it('accepts a consumer page-size default', () => {
        expect(buildDbQueryFromTableEvent(event({ pageSize: 0 }), { pageSize: 30 }).pageSize).toBe(30);
    });
});
