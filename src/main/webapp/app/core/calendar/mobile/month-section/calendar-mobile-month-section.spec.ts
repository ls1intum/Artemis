import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarMobileMonthSection } from './calendar-mobile-month-section';

describe('CalendarMobileMonthSection', () => {
    let component: CalendarMobileMonthSection;
    let fixture: ComponentFixture<CalendarMobileMonthSection>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarMobileMonthSection],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarMobileMonthSection);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
