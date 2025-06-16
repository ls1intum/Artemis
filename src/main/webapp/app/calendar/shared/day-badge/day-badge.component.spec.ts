import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DayBadgeComponent } from './day-badge.component';

describe('DayBadgeComponent', () => {
    let component: DayBadgeComponent;
    let fixture: ComponentFixture<DayBadgeComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [DayBadgeComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(DayBadgeComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
