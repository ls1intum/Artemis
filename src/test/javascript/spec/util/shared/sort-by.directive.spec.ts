import { Component, DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { ArtemisTestModule } from '../../test.module';

@Component({
    template: `
        <table>
            <thead>
                <tr jhiSort [(predicate)]="predicate" [(ascending)]="ascending" (sortChange)="transition($event)">
                    <th jhiSortBy="name">ID<fa-icon [icon]="faSort" /></th>
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
            imports: [ArtemisTestModule],
            declarations: [TestSortByDirectiveComponent, SortDirective, SortByDirective],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestSortByDirectiveComponent);
                component = fixture.componentInstance;
                tableHead = fixture.debugElement.query(By.directive(SortByDirective));
            });
    });

    it('should initialize predicate, order, icon when initial component predicate is _score', () => {
        // GIVEN
        component.predicate = '_score';
        const sortByDirective = tableHead.injector.get(SortByDirective);

        // WHEN
        fixture.detectChanges();

        // THEN
        expect(sortByDirective.jhiSortBy).toBe('name');
        expect(component.predicate).toBe('_score');
        expect(sortByDirective.iconComponent?.icon).toBe(faSort);
        expect(component.transition).not.toHaveBeenCalled();
    });

    it('should initialize predicate, order, icon when initial component predicate differs from column predicate', () => {
        // GIVEN
        component.predicate = 'id';
        const sortByDirective = tableHead.injector.get(SortByDirective);

        // WHEN
        fixture.detectChanges();

        // THEN
        expect(sortByDirective.jhiSortBy).toBe('name');
        expect(component.predicate).toBe('id');
        expect(sortByDirective.iconComponent?.icon).toBe(faSort);
        expect(component.transition).not.toHaveBeenCalled();
    });

    it('should initialize predicate, order, icon when initial component predicate is same as column predicate', () => {
        // GIVEN
        component.predicate = 'name';
        component.ascending = true;
        const sortByDirective = tableHead.injector.get(SortByDirective);

        // WHEN
        fixture.detectChanges();

        // THEN
        expect(sortByDirective.jhiSortBy).toBe('name');
        expect(component.predicate).toBe('name');
        expect(component.ascending).toBeTrue();
        expect(sortByDirective.iconComponent?.icon).toBe(faSortUp);
        expect(component.transition).not.toHaveBeenCalled();
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
        expect(sortByDirective.jhiSortBy).toBe('name');
        expect(component.predicate).toBe('name');
        expect(component.ascending).toBeTrue();
        expect(sortByDirective.iconComponent?.icon).toBe(faSortUp);
        expect(component.transition).toHaveBeenCalledOnce();
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
        expect(component.predicate).toBe('name');
        expect(component.ascending).toBeFalse();
        expect(sortByDirective.iconComponent?.icon).toBe(faSortDown);
        expect(component.transition).toHaveBeenCalledOnce();
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
        expect(component.predicate).toBe('name');
        expect(component.ascending).toBeTrue();
        expect(sortByDirective.iconComponent?.icon).toBe(faSortUp);
        expect(component.transition).toHaveBeenCalledTimes(2);
    });
});
