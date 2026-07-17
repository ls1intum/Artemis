import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { TumUiTableQueryEvent } from 'app/shared-ui/tum-ui/table/tum-ui-table.types';
import { buildDbQueryFromTableEvent } from 'app/shared-ui/tum-ui/table/tum-ui-table-request-builder';

function event(overrides: Partial<TumUiTableQueryEvent> = {}): TumUiTableQueryEvent {
    return { offset: 0, pageSize: 50, ...overrides };
}

describe('buildDbQueryFromTableEvent', () => {
    describe('offset → 0-based page conversion', () => {
        it('maps offset 0 to page 0', () => {
            expect(buildDbQueryFromTableEvent(event({ offset: 0, pageSize: 50 })).page).toBe(0);
        });

        it('maps a full-page offset to the next page', () => {
            expect(buildDbQueryFromTableEvent(event({ offset: 50, pageSize: 50 })).page).toBe(1);
            expect(buildDbQueryFromTableEvent(event({ offset: 100, pageSize: 25 })).page).toBe(4);
        });

        it('floors a partial offset (never rounds up or off-by-one)', () => {
            // 55 / 50 = 1.1 → page 1, not 2; 49 / 50 = 0.98 → page 0.
            expect(buildDbQueryFromTableEvent(event({ offset: 55, pageSize: 50 })).page).toBe(1);
            expect(buildDbQueryFromTableEvent(event({ offset: 49, pageSize: 50 })).page).toBe(0);
        });

        it('round-trips the offset = page * pageSize that the table emits', () => {
            const pageSize = 20;
            for (const page of [0, 1, 5, 42]) {
                expect(buildDbQueryFromTableEvent(event({ offset: page * pageSize, pageSize })).page).toBe(page);
            }
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

        it('keeps page 0 for a zero-offset zero-page-size event (the "|| 50" guard avoids divide-by-zero)', () => {
            // pageSize 0 → falls back to 50, so floor(0 / 50) = 0 (no NaN/divide-by-zero).
            expect(buildDbQueryFromTableEvent(event({ offset: 0, pageSize: 0 })).page).toBe(0);
        });
    });

    describe('sort field', () => {
        it('passes a concrete sort field through', () => {
            expect(buildDbQueryFromTableEvent(event({ sortField: 'name' })).sortedColumn).toBe('name');
        });

        it('defaults to "id" when the field is absent or blank', () => {
            expect(buildDbQueryFromTableEvent(event({ sortField: undefined })).sortedColumn).toBe('id');
            expect(buildDbQueryFromTableEvent(event({ sortField: '   ' })).sortedColumn).toBe('id');
        });
    });

    describe('sort direction', () => {
        it('maps "desc" to DESCENDING', () => {
            expect(buildDbQueryFromTableEvent(event({ sortDirection: 'desc' })).sortingOrder).toBe(SortingOrder.DESCENDING);
        });

        it('maps "asc" and an absent direction to ASCENDING', () => {
            expect(buildDbQueryFromTableEvent(event({ sortDirection: 'asc' })).sortingOrder).toBe(SortingOrder.ASCENDING);
            expect(buildDbQueryFromTableEvent(event({ sortDirection: undefined })).sortingOrder).toBe(SortingOrder.ASCENDING);
        });
    });

    describe('global filter', () => {
        it('trims the term', () => {
            expect(buildDbQueryFromTableEvent(event({ globalFilter: '  alice  ' })).searchTerm).toBe('alice');
        });

        it('produces an empty string when absent', () => {
            expect(buildDbQueryFromTableEvent(event({ globalFilter: undefined })).searchTerm).toBe('');
        });
    });
});
