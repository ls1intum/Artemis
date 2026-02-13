import { Component, DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';

@Component({
    template: `
        <table>
            <thead>
                <tr jhiSort [(predicate)]="predicate" [(ascending)]="ascending" (sortChange)="transition($event)"></tr>
            </thead>
        </table>
    `,
    imports: [SortDirective],
})
class TestSortDirectiveComponent {
    predicate?: string;
    ascending?: boolean;
    transition = vi.fn();
}

describe('Directive: SortDirective', () => {
    setupTestBed({ zoneless: true });
    let component: TestSortDirectiveComponent;
    let fixture: ComponentFixture<TestSortDirectiveComponent>;
    let tableRow: DebugElement;

    beforeEach(() => {
        TestBed.configureTestingModule({})
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestSortDirectiveComponent);
                component = fixture.componentInstance;
                tableRow = fixture.debugElement.query(By.directive(SortDirective));
            });
    });

    it('should update predicate, order and invoke callback function', () => {
        // GIVEN
        const sortDirective = tableRow.injector.get(SortDirective);

        // WHEN
        fixture.detectChanges();
        sortDirective.sort('ID');

        // THEN
        expect(component.predicate).toBe('ID');
        expect(component.ascending).toBeTruthy();
        expect(component.transition).toHaveBeenCalledOnce();
    });

    it('should change sort order to descending when same field is sorted again', () => {
        // GIVEN
        const sortDirective = tableRow.injector.get(SortDirective);

        // WHEN
        fixture.detectChanges();
        sortDirective.sort('ID');
        // sort again
        sortDirective.sort('ID');

        // THEN
        expect(component.predicate).toBe('ID');
        expect(component.ascending).toBeFalsy();
        expect(component.transition).toHaveBeenCalledTimes(2);
    });

    it('should change sort order to ascending when different field is sorted', () => {
        // GIVEN
        const sortDirective = tableRow.injector.get(SortDirective);

        // WHEN
        fixture.detectChanges();
        sortDirective.sort('ID');
        // sort again
        sortDirective.sort('NAME');

        // THEN
        expect(component.predicate).toBe('NAME');
        expect(component.ascending).toBeTruthy();
        expect(component.transition).toHaveBeenCalledTimes(2);
    });
});
