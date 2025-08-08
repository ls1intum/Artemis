import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective } from 'ng-mocks';
import { CalendarMobileMonthPresentation } from './calendar-mobile-month-presentation.component';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CalendarEventService } from 'app/core/calendar/shared/service/calendar-event.service';
import { CalendarEvent, CalendarEventSubtype, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { MockCalendarEventService } from 'test/helpers/mocks/service/mock-calendar-event.service';

describe('CalendarMobileMonthPresentation', () => {
    let fixture: ComponentFixture<CalendarMobileMonthPresentation>;
    let component: CalendarMobileMonthPresentation;
    let mockMap: Map<string, CalendarEvent[]>;

    const referenceDate = dayjs('2025-05-15 10:30');
    const events: CalendarEvent[] = [
        new CalendarEvent(CalendarEventType.Exam, CalendarEventSubtype.StartAndEndDate, 'Exam', referenceDate, referenceDate.add(1, 'hour')),
        new CalendarEvent(CalendarEventType.Lecture, CalendarEventSubtype.StartAndEndDate, 'Lecture 1', referenceDate.subtract(4, 'hour'), referenceDate.subtract(2, 'hour')),
        new CalendarEvent(CalendarEventType.Lecture, CalendarEventSubtype.StartAndEndDate, 'Lecture 2', referenceDate.subtract(2, 'hour'), referenceDate),
        new CalendarEvent(CalendarEventType.Lecture, CalendarEventSubtype.StartAndEndDate, 'Lecture 3', referenceDate, referenceDate.add(2, 'hour')),
        new CalendarEvent(CalendarEventType.Tutorial, CalendarEventSubtype.StartAndEndDate, 'Tutorial 1', referenceDate.add(1, 'day'), referenceDate.add(1, 'day').add(1, 'hour')),
        new CalendarEvent(
            CalendarEventType.Tutorial,
            CalendarEventSubtype.StartAndEndDate,
            'Tutorial 2',
            referenceDate.add(1, 'day').add(2, 'hour'),
            referenceDate.add(1, 'day').add(3, 'hour'),
        ),
        new CalendarEvent(
            CalendarEventType.Tutorial,
            CalendarEventSubtype.StartAndEndDate,
            'Tutorial 3',
            referenceDate.add(1, 'day').add(3, 'hour'),
            referenceDate.add(1, 'day').add(4, 'hour'),
        ),
        new CalendarEvent(CalendarEventType.TextExercise, CalendarEventSubtype.StartDate, 'Text Exercise', referenceDate.add(2, 'day')),
    ];

    beforeAll(() => {
        mockMap = new Map();
        for (const event of events) {
            const key = event.startDate.format('YYYY-MM-DD');
            const existing = mockMap.get(key) ?? [];
            existing.push(event);
            mockMap.set(key, existing);
        }
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarMobileMonthPresentation],
            declarations: [MockComponent(CalendarDayBadgeComponent), MockDirective(TranslateDirective)],
            providers: [
                {
                    provide: CalendarEventService,
                    useFactory: () => new MockCalendarEventService(mockMap),
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarMobileMonthPresentation);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('firstDayOfMonth', dayjs('2025-05-01'));
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render 5 week rows with 7 days each', () => {
        const weekRows = fixture.debugElement.queryAll(By.css('.week-row'));
        expect(weekRows).toHaveLength(5);
        for (const row of weekRows) {
            const days = row.queryAll(By.css('.day, .day-placeholder'));
            expect(days).toHaveLength(7);
        }
    });

    it('should render the correct number of event cells', () => {
        const eventCells = fixture.debugElement.queryAll(By.css('.event-cell'));
        expect(eventCells).toHaveLength(events.length - 2);
    });

    it('should render ellipsis when more than 3 events exist', () => {
        const ellipsisElements = fixture.debugElement.queryAll(By.css('.ellipsis'));
        expect(ellipsisElements).toHaveLength(1);
    });

    it('should emit selected day on click', () => {
        const emitSpy = jest.spyOn(component.selectDay, 'emit');

        const dayCell = fixture.debugElement.query(By.css('.day'));
        expect(dayCell).toBeTruthy();

        dayCell.nativeElement.click();
        fixture.detectChanges();

        expect(emitSpy).toHaveBeenCalledOnce();
        const emittedDay = emitSpy.mock.calls[0][0];
        expect(emittedDay).toBeDefined();
        expect(dayjs.isDayjs(emittedDay)).toBeTrue();
    });
});
