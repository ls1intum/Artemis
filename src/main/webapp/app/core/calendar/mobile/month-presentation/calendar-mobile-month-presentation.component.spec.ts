import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarMobileMonthPresentation } from './calendar-mobile-month-presentation.component';

describe('CalendarMobileMonthSection', () => {
    let component: CalendarMobileMonthPresentation;
    let fixture: ComponentFixture<CalendarMobileMonthPresentation>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarMobileMonthPresentation],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarMobileMonthPresentation);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
