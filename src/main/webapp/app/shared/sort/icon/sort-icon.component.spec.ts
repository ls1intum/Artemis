import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { SortIconComponent } from 'app/shared/sort/icon/sort-icon.component';
import { SortingOrder } from 'app/shared/table/pageable-table';

describe('SortIconComponent', () => {
    let component: SortIconComponent;
    let fixture: ComponentFixture<SortIconComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SortIconComponent, FontAwesomeModule],
        }).compileComponents();

        fixture = TestBed.createComponent(SortIconComponent);
        component = fixture.componentInstance;
    });

    it('should set isAscending to true when direction is Ascending', () => {
        fixture.componentRef.setInput('direction', SortingOrder.ASCENDING);
        fixture.detectChanges();
        expect(component.isAscending()).toBeTrue();
        expect(component.isDescending()).toBeFalse();
    });

    it('should set isDescending to true when direction is Descending', () => {
        fixture.componentRef.setInput('direction', SortingOrder.DESCENDING);
        fixture.detectChanges();
        expect(component.isDescending()).toBeTrue();
        expect(component.isAscending()).toBeFalse();
    });

    it('should set both isAscending and isDescending to false when direction is "none"', () => {
        fixture.componentRef.setInput('direction', 'none');
        fixture.detectChanges();
        expect(component.isAscending()).toBeFalse();
        expect(component.isDescending()).toBeFalse();
    });
});
