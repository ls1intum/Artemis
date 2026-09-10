import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideArtemisTumUiTranslator } from 'app/shared-ui/tum-ui-integration/artemis-tum-ui-translator';
import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { toHolidays } from 'app/tutorialgroup/manage/holidays/holiday.model';
import { HolidayDialogComponent, HolidaySubmission } from 'app/tutorialgroup/manage/holidays/holiday-dialog/holiday-dialog.component';

const TIME_ZONE = 'Europe/Berlin';

function holidayOf(start: string, end: string, reason: string) {
    const freePeriod = new TutorialGroupFreePeriod();
    freePeriod.id = 3;
    freePeriod.start = dayjs.utc(start);
    freePeriod.end = dayjs.utc(end);
    freePeriod.reason = reason;
    return toHolidays([freePeriod], TIME_ZONE)[0];
}

describe('HolidayDialogComponent', () => {
    let fixture: ComponentFixture<HolidayDialogComponent>;
    let component: HolidayDialogComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HolidayDialogComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideArtemisTumUiTranslator()],
        }).compileComponents();

        fixture = TestBed.createComponent(HolidayDialogComponent);
        component = fixture.componentInstance;
    });

    /** ngModel writes to the DOM asynchronously, so the form is only settled after a stable tick. */
    async function open(): Promise<void> {
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    }

    it('should open a new holiday on the whole day, so the common case needs no further input', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-04').startOf('day'));
        await open();

        expect(component['start']()!.format('YYYY-MM-DD HH:mm')).toBe('2025-12-04 00:00');
        expect(component['end']()!.format('YYYY-MM-DD HH:mm')).toBe('2025-12-04 23:59');
    });

    it('should load the whole span of the holiday being edited, not just its first day', async () => {
        fixture.componentRef.setInput('holiday', holidayOf('2025-12-16T23:00:00', '2025-12-31T22:59:00', 'Christmas holidays'));
        await open();

        expect(component['start']()!.format('YYYY-MM-DD')).toBe('2025-12-17');
        expect(component['end']()!.format('YYYY-MM-DD')).toBe('2025-12-31');
        expect(component['spansMultipleDays']()).toBe(true);
    });

    it('should keep the times when a holiday narrowed to part of a day moves to another date', async () => {
        fixture.componentRef.setInput('holiday', holidayOf('2025-12-04T08:15:00', '2025-12-04T12:45:00', 'Dies Academicus'));
        await open();

        component['onStartChange'](dayjs('2025-12-10').startOf('day').set('hour', 9).set('minute', 15));

        expect(component['end']()!.format('YYYY-MM-DD HH:mm')).toBe('2025-12-10 13:45');
    });

    it('should carry a single-day holiday along when its start moves', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-04').startOf('day'));
        await open();

        component['onStartChange'](dayjs('2025-12-10').startOf('day'));

        expect(component['end']()!.format('YYYY-MM-DD HH:mm')).toBe('2025-12-10 23:59');
        expect(component['spansMultipleDays']()).toBe(false);
    });

    it('should leave a multi-day span alone when its start moves within the range', async () => {
        fixture.componentRef.setInput('holiday', holidayOf('2025-12-16T23:00:00', '2025-12-31T22:59:00', 'Christmas holidays'));
        await open();

        component['onStartChange'](dayjs('2025-12-18').startOf('day'));

        expect(component['end']()!.format('YYYY-MM-DD')).toBe('2025-12-31');
    });

    it('should refuse a span whose end is not after its start', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-04').startOf('day'));
        await open();
        component['reason'].set('Dies Academicus');

        component['onEndChange'](dayjs('2025-12-01').startOf('day'));
        fixture.detectChanges();

        expect(component['canSave']()).toBe(false);
        expect(fixture.debugElement.query(By.css('[data-testid="holiday-range-error"]'))).not.toBeNull();
    });

    it('should refuse to save without a reason, since the reason is what students are shown', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-04').startOf('day'));
        await open();

        expect((fixture.debugElement.query(By.css('[data-testid="holiday-submit"]')).nativeElement as HTMLButtonElement).disabled).toBe(true);
    });

    it('should emit the span exactly as it will be stored', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-22').startOf('day'));
        await open();
        let submission: HolidaySubmission | undefined;
        component.save.subscribe((value) => (submission = value));

        component['onEndChange'](dayjs('2026-01-05').startOf('day').set('hour', 23).set('minute', 59));
        component['reason'].set('  Christmas holidays  ');
        fixture.detectChanges();
        fixture.debugElement.query(By.css('[data-testid="holiday-submit"]')).nativeElement.click();

        expect(submission?.start.format('YYYY-MM-DD HH:mm')).toBe('2025-12-22 00:00');
        expect(submission?.end.format('YYYY-MM-DD HH:mm')).toBe('2026-01-05 23:59');
        // Trimmed, so trailing whitespace never reaches the students.
        expect(submission?.reason).toBe('Christmas holidays');
    });

    it('should announce the chosen span so the page can count what it cancels', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-04').startOf('day'));
        const spans: { start: dayjs.Dayjs; end: dayjs.Dayjs }[] = [];
        component.selectedSpanChange.subscribe((span) => spans.push(span));

        await open();

        expect(spans.length).toBeGreaterThan(0);
        expect(spans.at(-1)!.start.format('YYYY-MM-DD')).toBe('2025-12-04');
    });

    it('should reset a cancelled edit, so it does not leak into the next holiday created', async () => {
        fixture.componentRef.setInput('holiday', holidayOf('2025-12-04T08:15:00', '2025-12-04T12:45:00', 'Dies Academicus'));
        await open();
        fixture.componentRef.setInput('visible', false);
        fixture.detectChanges();

        fixture.componentRef.setInput('holiday', undefined);
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-20').startOf('day'));
        await open();

        expect(component['reason']()).toBe('');
        expect(component['start']()!.format('YYYY-MM-DD HH:mm')).toBe('2025-12-20 00:00');
    });
});
