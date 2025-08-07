import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarEventsPerDaySectionComponent } from './calendar-events-per-day-section.component';

describe('EventsPerDayPresentation', () => {
    let component: CalendarEventsPerDaySectionComponent;
    let fixture: ComponentFixture<CalendarEventsPerDaySectionComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarEventsPerDaySectionComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarEventsPerDaySectionComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
