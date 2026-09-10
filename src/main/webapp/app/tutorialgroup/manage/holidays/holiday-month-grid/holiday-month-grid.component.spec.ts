import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideArtemisTumUiTranslator } from 'app/shared-ui/tum-ui-integration/artemis-tum-ui-translator';
import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { holidaysByDay, toHolidays } from 'app/tutorialgroup/manage/holidays/holiday.model';
import { HolidayMonthGridComponent } from 'app/tutorialgroup/manage/holidays/holiday-month-grid/holiday-month-grid.component';

const TIME_ZONE = 'Europe/Berlin';

function period(id: number, start: string, end: string, reason = 'Christmas holidays'): TutorialGroupFreePeriod {
    const freePeriod = new TutorialGroupFreePeriod();
    freePeriod.id = id;
    freePeriod.start = dayjs.utc(start);
    freePeriod.end = dayjs.utc(end);
    freePeriod.reason = reason;
    return freePeriod;
}

describe('HolidayMonthGridComponent', () => {
    let fixture: ComponentFixture<HolidayMonthGridComponent>;

    /** December 2025 starts on a Monday and ends on a Wednesday, so the grid spills into both neighbouring months. */
    const december = dayjs('2025-12-01').startOf('month');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HolidayMonthGridComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideArtemisTumUiTranslator()],
        }).compileComponents();

        fixture = TestBed.createComponent(HolidayMonthGridComponent);
        fixture.componentRef.setInput('displayedMonth', december);
        fixture.componentRef.setInput('holidaysByDay', new Map());
        fixture.componentRef.setInput('sessionCountsByDay', new Map());
        fixture.componentRef.setInput('today', dayjs('2025-12-10').startOf('day'));
        fixture.detectChanges();
    });

    it('should render whole weeks, so the grid is always a multiple of seven days', () => {
        const days = fixture.debugElement.queryAll(By.css('[data-day]'));

        expect(days.length % 7).toBe(0);
        expect(days.length).toBeGreaterThanOrEqual(28);
    });

    it('should mark the days that belong to the neighbouring months', () => {
        const outsideDays = fixture.debugElement.queryAll(By.css('.holiday-grid-day-outside'));

        // 1 December is a Monday, so only the days after 31 December fall outside.
        expect(outsideDays.length).toBeGreaterThan(0);
        expect(fixture.debugElement.query(By.css('[data-day="2025-12-15"]')).classes['holiday-grid-day-outside']).toBeFalsy();
    });

    it('should render a holiday on the day it falls on', () => {
        const holidays = toHolidays([period(1, '2025-12-16T23:00:00', '2025-12-17T22:59:00')], TIME_ZONE);
        fixture.componentRef.setInput('holidaysByDay', holidaysByDay(holidays));
        fixture.detectChanges();

        const day = fixture.debugElement.query(By.css('[data-day="2025-12-17"]'));

        expect(day.attributes['data-has-holiday']).toBe('true');
        expect(day.nativeElement.textContent).toContain('Christmas holidays');
    });

    it('should show the session count only on days without a holiday, so the holiday is what the day reads as', () => {
        const holidays = toHolidays([period(1, '2025-12-16T23:00:00', '2025-12-17T22:59:00')], TIME_ZONE);
        fixture.componentRef.setInput('holidaysByDay', holidaysByDay(holidays));
        fixture.componentRef.setInput(
            'sessionCountsByDay',
            new Map([
                ['2025-12-17', 7],
                ['2025-12-18', 7],
            ]),
        );
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-day="2025-12-17"] .holiday-grid-day-sessions'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-day="2025-12-18"] .holiday-grid-day-sessions'))).not.toBeNull();
    });

    it('should emit the next month when the forward control is used', () => {
        let emitted: dayjs.Dayjs | undefined;
        fixture.componentInstance.monthChange.subscribe((month) => (emitted = month));

        fixture.debugElement.query(By.css('[data-testid="holiday-calendar-next"]')).nativeElement.click();

        expect(emitted?.format('YYYY-MM')).toBe('2026-01');
    });

    it('should jump to the month of today rather than to the current month of the reader', () => {
        let emitted: dayjs.Dayjs | undefined;
        fixture.componentInstance.monthChange.subscribe((month) => (emitted = month));

        fixture.debugElement.query(By.css('[data-testid="holiday-calendar-today"]')).nativeElement.click();

        expect(emitted?.format('YYYY-MM')).toBe('2025-12');
    });

    it('should offer to create a holiday when an empty day is clicked', () => {
        let emitted: dayjs.Dayjs | undefined;
        fixture.componentInstance.daySelected.subscribe((day) => (emitted = day));

        fixture.debugElement.query(By.css('[data-day="2025-12-09"]')).nativeElement.click();

        expect(emitted?.format('YYYY-MM-DD')).toBe('2025-12-09');
    });

    it('should open the existing holiday when a day that already has one is clicked', () => {
        const holidays = toHolidays([period(4, '2025-12-16T23:00:00', '2025-12-17T22:59:00')], TIME_ZONE);
        fixture.componentRef.setInput('holidaysByDay', holidaysByDay(holidays));
        fixture.detectChanges();
        let selectedId: number | undefined;
        fixture.componentInstance.holidaySelected.subscribe((occurrence) => (selectedId = occurrence.period.id));

        fixture.debugElement.query(By.css('[data-day="2025-12-17"]')).nativeElement.click();

        expect(selectedId).toBe(4);
    });

    it('should ignore a click on a day of a neighbouring month', () => {
        let daySelected = false;
        fixture.componentInstance.daySelected.subscribe(() => (daySelected = true));

        // 1 January 2026 completes the last week of the December grid.
        fixture.debugElement.query(By.css('[data-day="2026-01-01"]')).nativeElement.click();

        expect(daySelected).toBe(false);
    });
});
