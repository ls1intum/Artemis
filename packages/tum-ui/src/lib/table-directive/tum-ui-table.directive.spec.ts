import { WritableSignal, inputBinding, outputBinding, signal } from '@angular/core';
import { DirectiveFixture, TestBed } from '@angular/core/testing';
import { TumUiTableDirective, TumUiTableSortEvent } from './tum-ui-table.directive';

describe('TumUiTableDirective', () => {
    let fixture: DirectiveFixture<TumUiTableDirective>;
    let sortField: WritableSignal<string | undefined>;
    let sortOrder: WritableSignal<number>;
    let events: TumUiTableSortEvent[];

    beforeEach(() => {
        sortField = signal<string | undefined>(undefined);
        sortOrder = signal(1);
        events = [];
        // The selector is table[tumUiTable], so TestBed creates the host as a <table> without being told.
        fixture = TestBed.createDirective(TumUiTableDirective, {
            bindings: [inputBinding('sortField', sortField), inputBinding('sortOrder', sortOrder), outputBinding<TumUiTableSortEvent>('sortChange', (event) => events.push(event))],
        });
        fixture.detectChanges();
    });

    it('toggles the order when re-sorting the active field', () => {
        sortField.set('name');
        sortOrder.set(1);
        fixture.detectChanges();

        fixture.directiveInstance.requestSort('name');
        expect(events.at(-1)).toEqual({ field: 'name', order: -1 });

        sortOrder.set(-1);
        fixture.detectChanges();
        fixture.directiveInstance.requestSort('name');
        expect(events.at(-1)).toEqual({ field: 'name', order: 1 });
    });

    it('sorts a newly selected field ascending (defaultSortOrder)', () => {
        sortField.set('name');
        sortOrder.set(-1);
        fixture.detectChanges();

        fixture.directiveInstance.requestSort('email');
        expect(events.at(-1)).toEqual({ field: 'email', order: 1 });
    });
});
