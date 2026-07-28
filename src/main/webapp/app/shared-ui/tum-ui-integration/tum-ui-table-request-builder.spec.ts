import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { TumUiTableQueryEvent } from '@tumaet/ui-angular';
import { buildDbQueryFromTableEvent } from './tum-ui-table-request-builder';

function event(overrides: Partial<TumUiTableQueryEvent> = {}): TumUiTableQueryEvent {
    return { page: 0, pageSize: 50, ...overrides };
}

describe('buildDbQueryFromTableEvent', () => {
    describe('page', () => {
        it('passes the 0-based page through unchanged', () => {
            expect(buildDbQueryFromTableEvent(event({ page: 0 })).page).toBe(0);
            expect(buildDbQueryFromTableEvent(event({ page: 4 })).page).toBe(4);
        });
    });

    describe('page size', () => {
        it('uses the event page size', () => {
            expect(buildDbQueryFromTableEvent(event({ pageSize: 20 })).pageSize).toBe(20);
        });

        it('falls back to the provided default, then 50, for a falsy page size', () => {
            expect(buildDbQueryFromTableEvent(event({ pageSize: 0 }), { pageSize: 30 }).pageSize).toBe(30);
            expect(buildDbQueryFromTableEvent(event({ pageSize: 0 })).pageSize).toBe(50);
        });
    });

    describe('sort', () => {
        it('passes a concrete sort field and direction through', () => {
            const query = buildDbQueryFromTableEvent(event({ sort: { field: 'name', direction: 'asc' } }));
            expect(query.sortedColumn).toBe('name');
            expect(query.sortingOrder).toBe(SortingOrder.ASCENDING);
        });

        it('maps "desc" to DESCENDING', () => {
            expect(buildDbQueryFromTableEvent(event({ sort: { field: 'name', direction: 'desc' } })).sortingOrder).toBe(SortingOrder.DESCENDING);
        });

        it('defaults the column to "id" and the order to ASCENDING when unsorted', () => {
            const query = buildDbQueryFromTableEvent(event({ sort: undefined }));
            expect(query.sortedColumn).toBe('id');
            expect(query.sortingOrder).toBe(SortingOrder.ASCENDING);
        });

        it('defaults a blank sort field to "id"', () => {
            expect(buildDbQueryFromTableEvent(event({ sort: { field: '   ', direction: 'asc' } })).sortedColumn).toBe('id');
        });
    });

    describe('search term', () => {
        it('trims the term', () => {
            expect(buildDbQueryFromTableEvent(event({ searchTerm: '  alice  ' })).searchTerm).toBe('alice');
        });

        it('produces an empty string when absent', () => {
            expect(buildDbQueryFromTableEvent(event({ searchTerm: undefined })).searchTerm).toBe('');
        });
    });
});
