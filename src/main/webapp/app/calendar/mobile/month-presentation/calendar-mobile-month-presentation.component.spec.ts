import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective } from 'ng-mocks';
import { CalendarMobileMonthPresentationComponent } from './calendar-mobile-month-presentation.component';
import { CalendarDayBadgeComponent } from 'app/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { IdentifiableCalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';
import { MockCalendarService } from 'test/helpers/mocks/service/mock-calendar.service';
import { CalendarEvent } from 'app/openapi/model/calendarEvent';

describe('CalendarMobileMonthPresentationComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CalendarMobileMonthPresentationComponent>;
    let component: CalendarMobileMonthPresentationComponent;
    let mockMap: Map<string, IdentifiableCalendarEvent[]>;

    const referenceDate = dayjs('2025-05-15 10:30');
    const events: IdentifiableCalendarEvent[] = [
        new IdentifiableCalendarEvent(CalendarEvent.TypeEnum.Exam, 'Exam', referenceDate, referenceDate.add(1, 'hour')),
        new IdentifiableCalendarEvent(CalendarEvent.TypeEnum.Lecture, 'Lecture 1', referenceDate.subtract(4, 'hour'), referenceDate.subtract(2, 'hour')),
        new IdentifiableCalendarEvent(CalendarEvent.TypeEnum.Lecture, 'Lecture 2', referenceDate.subtract(2, 'hour'), referenceDate),
        new IdentifiableCalendarEvent(CalendarEvent.TypeEnum.Lecture, 'Lecture 3', referenceDate, referenceDate.add(2, 'hour')),
        new IdentifiableCalendarEvent(CalendarEvent.TypeEnum.Tutorial, 'Tutorial 1', referenceDate.add(1, 'day'), referenceDate.add(1, 'day').add(1, 'hour')),
        new IdentifiableCalendarEvent(CalendarEvent.TypeEnum.Tutorial, 'Tutorial 2', referenceDate.add(1, 'day').add(2, 'hour'), referenceDate.add(1, 'day').add(3, 'hour')),
        new IdentifiableCalendarEvent(CalendarEvent.TypeEnum.Tutorial, 'Tutorial 3', referenceDate.add(1, 'day').add(3, 'hour'), referenceDate.add(1, 'day').add(4, 'hour')),
        new IdentifiableCalendarEvent(CalendarEvent.TypeEnum.TextExercise, 'Start: Text Exercise', referenceDate.add(2, 'day')),
    ];

    afterEach(() => {
        vi.restoreAllMocks();
    });

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
            imports: [CalendarMobileMonthPresentationComponent, MockComponent(CalendarDayBadgeComponent), MockDirective(TranslateDirective)],
            providers: [
                {
                    provide: CalendarService,
                    useFactory: () => new MockCalendarService(mockMap),
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarMobileMonthPresentationComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('firstDateOfCurrentMonth', dayjs('2025-05-01'));
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
        const emitSpy = vi.spyOn(component.onDateSelected, 'emit');

        const dayCell = fixture.debugElement.query(By.css('.day'));
        expect(dayCell).toBeTruthy();

        dayCell.nativeElement.click();
        fixture.detectChanges();

        expect(emitSpy).toHaveBeenCalledOnce();
        const emittedDay = emitSpy.mock.calls[0][0];
        expect(emittedDay).toBeDefined();
        expect(dayjs.isDayjs(emittedDay)).toBe(true);
    });
});
