import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarDayBadgeComponent } from './calendar-day-badge.component';

describe('DayBadgeComponent', () => {
    let component: CalendarDayBadgeComponent;
    let fixture: ComponentFixture<CalendarDayBadgeComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarDayBadgeComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarDayBadgeComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
