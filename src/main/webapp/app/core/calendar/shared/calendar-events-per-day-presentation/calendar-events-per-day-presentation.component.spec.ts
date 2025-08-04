import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarEventsPerDayPresentation } from './calendar-events-per-day-presentation.component';

describe('EventsPerDayPresentation', () => {
    let component: CalendarEventsPerDayPresentation;
    let fixture: ComponentFixture<CalendarEventsPerDayPresentation>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarEventsPerDayPresentation],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarEventsPerDayPresentation);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
