import { Component, DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';

@Component({
    template: `
        <table>
            <thead>
                <tr jhiSort [(predicate)]="predicate" [(ascending)]="ascending" (sortChange)="transition($event)">
                    <th jhiSortBy="name">ID<fa-icon [icon]="faSort"></fa-icon></th>
                </tr>
            </thead>
        </table>
    `,
})
class TestSortByDirectiveComponent {
    predicate?: string;
    ascending?: boolean;
    transition = jest.fn();
    faSort = faSort;
}

describe('Directive: SortByDirective', () => {
    let component: TestSortByDirectiveComponent;
    let fixture: ComponentFixture<TestSortByDirectiveComponent>;
    let tableHead: DebugElement;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [TestSortByDirectiveComponent, SortDirective, SortByDirective, FaIconComponent],
        });
        fixture = TestBed.createComponent(TestSortByDirectiveComponent);
        component = fixture.componentInstance;
        tableHead = fixture.debugElement.query(By.directive(SortByDirective));
    });

    it('should initialize predicate, order, icon when initial component predicate is _score', () => {
        // GIVEN
        component.predicate = '_score';
        const sortByDirective = tableHead.injector.get(SortByDirective);

        // WHEN
        fixture.detectChanges();

        // THEN
        expect(sortByDirective.jhiSortBy).toEqual('name');
        expect(component.predicate).toEqual('_score');
        expect(sortByDirective.iconComponent?.icon).toEqual(faSort);
        expect(component.transition).toHaveBeenCalledTimes(0);
    });

    it('should initialize predicate, order, icon when initial component predicate differs from column predicate', () => {
        // GIVEN
        component.predicate = 'id';
        const sortByDirective = tableHead.injector.get(SortByDirective);

        // WHEN
        fixture.detectChanges();

        // THEN
        expect(sortByDirective.jhiSortBy).toEqual('name');
        expect(component.predicate).toEqual('id');
        expect(sortByDirective.iconComponent?.icon).toEqual(faSort);
        expect(component.transition).toHaveBeenCalledTimes(0);
    });

    it('should initialize predicate, order, icon when initial component predicate is same as column predicate', () => {
        // GIVEN
        component.predicate = 'name';
        component.ascending = true;
        const sortByDirective = tableHead.injector.get(SortByDirective);

        // WHEN
        fixture.detectChanges();

        // THEN
        expect(sortByDirective.jhiSortBy).toEqual('name');
        expect(component.predicate).toEqual('name');
        expect(component.ascending).toEqual(true);
        expect(sortByDirective.iconComponent?.icon).toEqual(faSortUp);
        expect(component.transition).toHaveBeenCalledTimes(0);
    });

    it('should initialize predicate, order, icon when initial component predicate is _score and user clicks on column header', () => {
        // GIVEN
        component.predicate = '_score';
        component.ascending = true;
        const sortByDirective = tableHead.injector.get(SortByDirective);

        // WHEN
        fixture.detectChanges();
        tableHead.triggerEventHandler('click', null);
        fixture.detectChanges();

        // THEN
        expect(sortByDirective.jhiSortBy).toEqual('name');
        expect(component.predicate).toEqual('name');
        expect(component.ascending).toEqual(true);
        expect(sortByDirective.iconComponent?.icon).toEqual(faSortUp);
        expect(component.transition).toHaveBeenCalledTimes(1);
    });

    it('should update component predicate, order, icon when user clicks on column header', () => {
        // GIVEN
        component.predicate = 'name';
        component.ascending = true;
        const sortByDirective = tableHead.injector.get(SortByDirective);

        // WHEN
        fixture.detectChanges();
        tableHead.triggerEventHandler('click', null);
        fixture.detectChanges();

        // THEN
        expect(component.predicate).toEqual('name');
        expect(component.ascending).toEqual(false);
        expect(sortByDirective.iconComponent?.icon).toEqual(faSortDown);
        expect(component.transition).toHaveBeenCalledTimes(1);
    });

    it('should update component predicate, order, icon when user double clicks on column header', () => {
        // GIVEN
        component.predicate = 'name';
        component.ascending = true;
        const sortByDirective = tableHead.injector.get(SortByDirective);

        // WHEN
        fixture.detectChanges();

        // WHEN
        tableHead.triggerEventHandler('click', null);
        fixture.detectChanges();

        tableHead.triggerEventHandler('click', null);
        fixture.detectChanges();

        // THEN
        expect(component.predicate).toEqual('name');
        expect(component.ascending).toEqual(true);
        expect(sortByDirective.iconComponent?.icon).toEqual(faSortUp);
        expect(component.transition).toHaveBeenCalledTimes(2);
    });
});
