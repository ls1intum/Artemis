import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CalendarMonthPresentationComponent } from './calendar-month-presentation.component';
import { CalendarEventDummyService } from 'app/calendar/shared/service/calendar-event-dummy.service';
import { CalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';
import dayjs, { Dayjs } from 'dayjs/esm';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';

describe('CalendarDesktopMonthComponent', () => {
    let fixture: ComponentFixture<CalendarMonthPresentationComponent>;
    let component: CalendarMonthPresentationComponent;

    const testDate: Dayjs = dayjs('2025-06-01 10:30');
    const testEvent: CalendarEvent = {
        id: 'course-1',
        title: 'Test Event',
        startDate: testDate,
        endDate: testDate.add(1, 'hour'),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarMonthPresentationComponent],
            declarations: [MockPipe(ArtemisTranslatePipe)],
            providers: [CalendarEventDummyService],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarMonthPresentationComponent);
        component = fixture.componentInstance;

        const eventService = TestBed.inject(CalendarEventDummyService);
        jest.spyOn(eventService, 'getEventsOfDay').mockImplementation((day: Dayjs) => (day.isSame(testDate, 'day') ? [testEvent] : []));

        fixture.componentRef.setInput('firstDayOfCurrentMonth', testDate);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should compute correct number of weeks and days', () => {
        const weeks = component.weeks();
        expect(weeks).toHaveLength(6);
        expect(weeks.every((w) => w.length === 7)).toBeTrue();
    });

    it('should generate correct event map', () => {
        const key = testDate.format('YYYY-MM-DD');
        const map = component.eventMap();
        expect(map.has(key)).toBeTrue();
        expect(map.get(key)).toEqual([testEvent]);
    });

    it('should compute correct time string', () => {
        const timeString = component.getTimeString(testEvent.startDate);
        expect(timeString).toBe('10:30');
    });
});
