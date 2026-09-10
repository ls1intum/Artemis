import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideArtemisTumUiTranslator } from 'app/shared-ui/tum-ui-integration/artemis-tum-ui-translator';
import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { toHolidays } from 'app/tutorialgroup/manage/holidays/holiday.model';
import { HolidayListComponent } from 'app/tutorialgroup/manage/holidays/holiday-list/holiday-list.component';

const TIME_ZONE = 'Europe/Berlin';

function period(id: number, start: string, end: string, reason: string): TutorialGroupFreePeriod {
    const freePeriod = new TutorialGroupFreePeriod();
    freePeriod.id = id;
    freePeriod.start = dayjs.utc(start);
    freePeriod.end = dayjs.utc(end);
    freePeriod.reason = reason;
    return freePeriod;
}

describe('HolidayListComponent', () => {
    let fixture: ComponentFixture<HolidayListComponent>;

    const today = dayjs('2025-12-10').startOf('day');
    // One holiday before today and two after, so the upcoming filter has something to hide and something to show.
    const holidays = toHolidays(
        [
            period(1, '2025-11-30T23:00:00', '2025-12-01T22:59:00', 'Past holiday'),
            period(2, '2025-12-16T23:00:00', '2025-12-17T22:59:00', 'Christmas holidays'),
            period(3, '2025-12-24T23:00:00', '2025-12-25T22:59:00', 'Christmas Day'),
        ],
        TIME_ZONE,
    );

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HolidayListComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideArtemisTumUiTranslator()],
        }).compileComponents();

        fixture = TestBed.createComponent(HolidayListComponent);
        fixture.componentRef.setInput('holidays', holidays);
        fixture.componentRef.setInput('sessionCountsByDay', new Map([['2025-12-17', 7]]));
        fixture.componentRef.setInput('today', today);
        fixture.componentRef.setInput('filter', 'upcoming');
        fixture.detectChanges();
    });

    it('should hide holidays that are already past when filtering to upcoming', () => {
        const items = fixture.debugElement.queryAll(By.css('[data-testid="holiday-list-item"]'));

        expect(items).toHaveLength(2);
        expect(fixture.nativeElement.textContent).not.toContain('Past holiday');
    });

    it('should point out that past holidays are hidden, rather than dropping them silently', () => {
        expect(fixture.debugElement.query(By.css('[data-testid="holiday-list-past-note"]'))).not.toBeNull();
    });

    it('should not claim holidays are hidden when none are in the past', () => {
        const upcomingOnly = toHolidays([period(5, '2025-12-24T23:00:00', '2025-12-25T22:59:00', 'Christmas Day')], TIME_ZONE);
        fixture.componentRef.setInput('holidays', upcomingOnly);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-testid="holiday-list-past-note"]'))).toBeNull();
    });

    it('should show every holiday when filtering to all', () => {
        fixture.componentRef.setInput('filter', 'all');
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.css('[data-testid="holiday-list-item"]'))).toHaveLength(3);
        // The note explains a filter that is no longer applied, so it goes away with it.
        expect(fixture.debugElement.query(By.css('[data-testid="holiday-list-past-note"]'))).toBeNull();
    });

    it('should count a holiday that falls on today as upcoming', () => {
        const onToday = toHolidays([period(4, '2025-12-09T23:00:00', '2025-12-10T22:59:00', 'Today holiday')], TIME_ZONE);
        fixture.componentRef.setInput('holidays', onToday);
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.css('[data-testid="holiday-list-item"]'))).toHaveLength(1);
    });

    it('should show the session count only for days that hold sessions', () => {
        const tags = fixture.debugElement.queryAll(By.css('[data-testid="holiday-list-sessions"]'));

        expect(tags).toHaveLength(1);
    });

    it('should render an empty state when nothing matches the filter', () => {
        fixture.componentRef.setInput('holidays', []);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-testid="holiday-list-empty"]'))).not.toBeNull();
    });

    it('should list a holiday covering several days once rather than once per day', () => {
        const twoWeeks = toHolidays([period(6, '2025-12-21T23:00:00', '2026-01-04T22:59:00', 'Christmas holidays')], TIME_ZONE);
        fixture.componentRef.setInput('holidays', twoWeeks);
        fixture.detectChanges();

        const items = fixture.debugElement.queryAll(By.css('[data-testid="holiday-list-item"]'));

        expect(items).toHaveLength(1);
        // The row names the span rather than a weekday, so the reader can see it is not a single day.
        expect(items[0].nativeElement.textContent).toContain('–');
    });

    it('should add up the sessions across every day a holiday covers', () => {
        const twoDays = toHolidays([period(7, '2025-12-16T23:00:00', '2025-12-18T22:59:00', 'Break')], TIME_ZONE);
        fixture.componentRef.setInput('holidays', twoDays);
        fixture.componentRef.setInput(
            'sessionCountsByDay',
            new Map([
                ['2025-12-17', 7],
                ['2025-12-18', 5],
            ]),
        );
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.css('[data-testid="holiday-list-sessions"]'))).toHaveLength(1);
    });

    it('should offer edit and delete directly rather than behind a menu', () => {
        expect(fixture.debugElement.queryAll(By.css('[data-testid="holiday-edit"]'))).toHaveLength(2);
        expect(fixture.debugElement.queryAll(By.css('[data-testid="holiday-delete"]'))).toHaveLength(2);
    });

    it('should hand the whole holiday to the edit and delete handlers', () => {
        let edited: number | undefined;
        let deleted: number | undefined;
        fixture.componentInstance.editRequested.subscribe((holiday) => (edited = holiday.period.id));
        fixture.componentInstance.deleteRequested.subscribe((holiday) => (deleted = holiday.period.id));

        fixture.debugElement.queryAll(By.css('[data-testid="holiday-edit"]'))[0].nativeElement.click();
        fixture.debugElement.queryAll(By.css('[data-testid="holiday-delete"]'))[0].nativeElement.click();

        expect(edited).toBe(2);
        expect(deleted).toBe(2);
    });

    it('should ignore a cleared filter selection rather than leaving the list unfiltered', () => {
        let emitted: string | undefined;
        fixture.componentInstance.filterChange.subscribe((value) => (emitted = value));

        fixture.debugElement.query(By.css('[data-testid="holiday-list-filter"]')).triggerEventHandler('changed', undefined);

        expect(emitted).toBeUndefined();
    });
});
