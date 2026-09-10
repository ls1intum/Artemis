import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideArtemisTumUiTranslator } from 'app/shared-ui/tum-ui-integration/artemis-tum-ui-translator';
import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { toHolidays } from 'app/tutorialgroup/manage/holidays/holiday.model';
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

    const queryAll = (testId: string) => fixture.debugElement.queryAll(By.css(`[data-testid="${testId}"]`));
    const query = (testId: string) => fixture.debugElement.query(By.css(`[data-testid="${testId}"]`));

    function setHolidays(...periods: TutorialGroupFreePeriod[]): void {
        fixture.componentRef.setInput('holidays', toHolidays(periods, TIME_ZONE));
        fixture.detectChanges();
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HolidayMonthGridComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideArtemisTumUiTranslator()],
        }).compileComponents();

        fixture = TestBed.createComponent(HolidayMonthGridComponent);
        fixture.componentRef.setInput('displayedMonth', december);
        fixture.componentRef.setInput('holidays', []);
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
        const outsideDays = fixture.debugElement.queryAll(By.css('[data-outside-month]'));

        // 1 December is a Monday, so only the days after 31 December fall outside.
        expect(outsideDays.length).toBeGreaterThan(0);
        expect(fixture.debugElement.query(By.css('[data-day="2025-12-15"]')).attributes['data-outside-month']).toBeUndefined();
    });

    it('should render a holiday as one bar and mark the day it covers', () => {
        setHolidays(period(1, '2025-12-16T23:00:00', '2025-12-17T22:59:00'));

        expect(fixture.debugElement.query(By.css('[data-day="2025-12-17"]')).attributes['data-has-holiday']).toBe('true');
        const bars = queryAll('holiday-calendar-event');
        expect(bars).toHaveLength(1);
        expect(bars[0].nativeElement.textContent).toContain('Christmas holidays');
        expect(bars[0].attributes['data-span']).toBe('1');
    });

    it('should draw a run of days as a single bar rather than one per day', () => {
        setHolidays(period(1, '2025-12-16T23:00:00', '2025-12-19T22:59:00'));

        const bars = queryAll('holiday-calendar-event');

        // Three days, one bar: the reason is written once, at the width of the whole run.
        expect(bars).toHaveLength(1);
        expect(bars[0].attributes['data-span']).toBe('3');
        expect(bars[0].nativeElement.textContent).toContain('Christmas holidays');
    });

    it('should split a run at the week boundary and mark both halves as continuing', () => {
        // 19 to 23 December 2025 crosses from a Friday into the following Tuesday.
        setHolidays(period(1, '2025-12-18T23:00:00', '2025-12-23T22:59:00'));

        const bars = queryAll('holiday-calendar-event');

        expect(bars).toHaveLength(2);
        expect(bars[0].attributes['data-continues-after']).toBe('true');
        expect(bars[0].attributes['data-continues-before']).toBeUndefined();
        expect(bars[1].attributes['data-continues-before']).toBe('true');
        expect(bars[1].attributes['data-continues-after']).toBeUndefined();
    });

    it('should name a time only on the stretch that carries that end of the span', () => {
        // 25 December 00:00 to 29 December 04:59, which crosses from Thursday into the following Monday.
        setHolidays(period(1, '2025-12-24T23:00:00', '2025-12-29T03:59:00'));

        const bars = queryAll('holiday-calendar-event');
        expect(bars).toHaveLength(2);

        // The first stretch is cancelled outright and says nothing; only the one holding the end names a time.
        expect(bars[0].query(By.css('[data-testid="holiday-calendar-event-time"]'))).toBeNull();
        expect(bars[1].query(By.css('[data-testid="holiday-calendar-event-time"]'))).not.toBeNull();
    });

    it('should say nothing about times for a day held to an exclusive midnight', () => {
        // 16 December 00:00 to 17 December 00:00: the 16th is covered outright, so no time belongs on it.
        setHolidays(period(1, '2025-12-15T23:00:00', '2025-12-16T23:00:00'));

        expect(queryAll('holiday-calendar-event')).toHaveLength(1);
        expect(queryAll('holiday-calendar-event-time')).toHaveLength(0);
    });

    it('should print both times for a holiday confined to part of one day', () => {
        setHolidays(period(1, '2025-12-04T08:15:00', '2025-12-04T12:45:00', 'Dies Academicus'));

        // Both ends fall on this day, so the bar prints them rather than describing one of them.
        expect(query('holiday-calendar-event-time').nativeElement.textContent.trim()).toBe('09:15–13:45');
    });

    it('should say nothing about times when the run covers its days outright', () => {
        setHolidays(period(1, '2025-12-16T23:00:00', '2025-12-19T22:59:00'));

        expect(queryAll('holiday-calendar-event-time')).toHaveLength(0);
    });

    it('should stack two holidays that share a day rather than drawing them over each other', () => {
        setHolidays(period(1, '2025-12-17T08:00:00', '2025-12-17T09:00:00', 'Morning'), period(2, '2025-12-17T13:00:00', '2025-12-17T14:00:00', 'Afternoon'));

        const bars = queryAll('holiday-calendar-event');

        expect(bars).toHaveLength(2);
        // Different lanes means different vertical offsets, which is what keeps both readable.
        const tops = bars.map((bar) => (bar.nativeElement.parentElement as HTMLElement).style.top);
        expect(new Set(tops).size).toBe(2);
    });

    it('should show the session count only on days without a holiday, so the holiday is what the day reads as', () => {
        setHolidays(period(1, '2025-12-16T23:00:00', '2025-12-17T22:59:00'));
        fixture.componentRef.setInput(
            'sessionCountsByDay',
            new Map([
                ['2025-12-17', 7],
                ['2025-12-18', 7],
            ]),
        );
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-day="2025-12-17"] [data-testid="holiday-calendar-sessions"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-day="2025-12-18"] [data-testid="holiday-calendar-sessions"]'))).not.toBeNull();
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

    it('should open the holiday when its bar is clicked', () => {
        setHolidays(period(4, '2025-12-16T23:00:00', '2025-12-17T22:59:00'));
        let selectedId: number | undefined;
        fixture.componentInstance.holidaySelected.subscribe((holiday) => (selectedId = holiday.period.id));

        query('holiday-calendar-event').nativeElement.click();

        expect(selectedId).toBe(4);
    });

    it('should still offer to create on a day that already carries a holiday, so a second one is reachable', () => {
        setHolidays(period(4, '2025-12-16T23:00:00', '2025-12-17T22:59:00'));
        let created: dayjs.Dayjs | undefined;
        let edited = false;
        fixture.componentInstance.daySelected.subscribe((day) => (created = day));
        fixture.componentInstance.holidaySelected.subscribe(() => (edited = true));

        // The cell, not the bar: the bar stops the click so the two actions stay separate.
        fixture.debugElement.query(By.css('[data-day="2025-12-17"]')).nativeElement.click();

        expect(created?.format('YYYY-MM-DD')).toBe('2025-12-17');
        expect(edited).toBe(false);
    });

    it('should leave the day out when a holiday ends exactly at its midnight', () => {
        // The end is exclusive, so 17 December 00:00 cancels nothing on the 17th and the day keeps its own count.
        setHolidays(period(1, '2025-12-15T23:00:00', '2025-12-16T23:00:00'));

        expect(fixture.debugElement.query(By.css('[data-day="2025-12-17"]')).attributes['data-has-holiday']).toBeUndefined();
        expect(fixture.debugElement.query(By.css('[data-day="2025-12-16"]')).attributes['data-has-holiday']).toBe('true');
        expect(queryAll('holiday-calendar-event')[0].attributes['data-span']).toBe('1');
    });

    it('should ignore a click on a day of a neighbouring month', () => {
        let daySelected = false;
        fixture.componentInstance.daySelected.subscribe(() => (daySelected = true));

        // 1 January 2026 completes the last week of the December grid.
        fixture.debugElement.query(By.css('[data-day="2026-01-01"]')).nativeElement.click();

        expect(daySelected).toBe(false);
    });
});
