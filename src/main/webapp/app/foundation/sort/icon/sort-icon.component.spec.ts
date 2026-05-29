import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { SortIconComponent } from 'app/foundation/sort/icon/sort-icon.component';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('SortIconComponent', () => {
    setupTestBed({ zoneless: true });
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
        expect(component.isAscending()).toBe(true);
        expect(component.isDescending()).toBe(false);
    });

    it('should set isDescending to true when direction is Descending', () => {
        fixture.componentRef.setInput('direction', SortingOrder.DESCENDING);
        fixture.detectChanges();
        expect(component.isDescending()).toBe(true);
        expect(component.isAscending()).toBe(false);
    });

    it('should set both isAscending and isDescending to false when direction is "none"', () => {
        fixture.componentRef.setInput('direction', 'none');
        fixture.detectChanges();
        expect(component.isAscending()).toBe(false);
        expect(component.isDescending()).toBe(false);
    });
});
