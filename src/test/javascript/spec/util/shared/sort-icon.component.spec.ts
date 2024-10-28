import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { SortIconComponent } from 'app/shared/sort/sort-icon.component';

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

    it('should set isAscending to true when direction is "asc"', () => {
        fixture.componentRef.setInput('direction', 'asc');
        fixture.detectChanges();
        expect(component.isAscending()).toBeTrue();
        expect(component.isDescending()).toBeFalse();
    });

    it('should set isDescending to true when direction is "desc"', () => {
        fixture.componentRef.setInput('direction', 'desc');
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
