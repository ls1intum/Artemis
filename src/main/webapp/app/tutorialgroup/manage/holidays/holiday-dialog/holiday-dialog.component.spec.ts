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

function holidayOf(start: string, end: string, reason: string, id = 3) {
    const freePeriod = new TutorialGroupFreePeriod();
    freePeriod.id = id;
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

    /**
     * The form fills itself in an effect, so the DOM is only settled after a stable tick. The popover portals it into
     * an overlay, but through the component's own view container, so it stays in the debug tree and this still finds it.
     */
    const query = (testId: string) => fixture.debugElement.query(By.css(`[data-testid="${testId}"]`));

    /** Something for the popover to point at; a popover without an origin has nothing to open against. */
    let origin: HTMLElement;

    async function open(): Promise<void> {
        origin ??= document.body.appendChild(document.createElement('button'));
        fixture.componentRef.setInput('origin', origin);
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

    it('should open a dragged run on its whole span, not just the day it started on', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-22').startOf('day'));
        fixture.componentRef.setInput('initialLastDay', dayjs('2025-12-26').startOf('day'));
        await open();

        expect(component['start']()!.format('YYYY-MM-DD HH:mm')).toBe('2025-12-22 00:00');
        expect(component['end']()!.format('YYYY-MM-DD HH:mm')).toBe('2025-12-26 23:59');
    });

    describe('overlapping an existing holiday', () => {
        // The server refuses an overlap and answers 400, which the page can only report as "bad request"; the clash is
        // caught here instead, so the reader is told which holiday is in the way before anything is sent.
        const existing = () => [holidayOf('2025-12-23T23:00:00', '2025-12-27T22:59:00', 'Christmas', 7)];

        it('should refuse to save a span that covers an existing holiday, and name it', async () => {
            fixture.componentRef.setInput('existingHolidays', existing());
            fixture.componentRef.setInput('initialDay', dayjs('2025-12-22').startOf('day'));
            fixture.componentRef.setInput('initialLastDay', dayjs('2025-12-26').startOf('day'));
            await open();
            component['reason'].set('Winter break');
            fixture.detectChanges();

            expect(component['clashingHoliday']()?.reason).toBe('Christmas');
            expect(component['canSave']()).toBe(false);
            expect(query('holiday-overlap-error')).not.toBeNull();
        });

        it('should allow a span that begins exactly where an existing one ends, as the server does', async () => {
            // Exactly on the line, not a minute either side of it: this holiday ends at midnight on the 24th (a span
            // stored elsewhere can end that way) and the new one starts at that same midnight. The server compares
            // strictly at both ends, so the two sit beside each other; comparing inclusively here would refuse a span
            // the server would have taken.
            fixture.componentRef.setInput('existingHolidays', [holidayOf('2025-12-19T23:00:00', '2025-12-23T23:00:00', 'Before Christmas', 7)]);
            fixture.componentRef.setInput('initialDay', dayjs('2025-12-24').startOf('day'));
            await open();
            component['reason'].set('Christmas');
            fixture.detectChanges();

            expect(component['start']()!.format('YYYY-MM-DD HH:mm')).toBe('2025-12-24 00:00');
            expect(component['clashingHoliday']()).toBeUndefined();
            expect(component['canSave']()).toBe(true);
        });

        it('should not count the holiday being edited as a clash with itself', async () => {
            const holiday = holidayOf('2025-12-23T23:00:00', '2025-12-27T22:59:00', 'Christmas', 7);
            fixture.componentRef.setInput('existingHolidays', [holiday]);
            fixture.componentRef.setInput('holiday', holiday);
            await open();

            expect(component['clashingHoliday']()).toBeUndefined();
            expect(component['canSave']()).toBe(true);
        });
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

    it('should carry a single day ending at an exclusive midnight rather than stretching it', async () => {
        // 16 December 00:00 to 17 December 00:00 is one day. Reading the raw end would call it two, and moving the
        // start back to the 10th would leave the end where it was - one holiday becoming seven days.
        fixture.componentRef.setInput('holiday', holidayOf('2025-12-15T23:00:00', '2025-12-16T23:00:00', 'One day'));
        await open();
        expect(component['spansMultipleDays']()).toBe(false);

        component['onStartChange'](dayjs('2025-12-10').startOf('day'));

        expect(component['end']()!.format('YYYY-MM-DD HH:mm')).toBe('2025-12-11 00:00');
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

    it('should refuse to save while a date field holds text that does not parse', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-04').startOf('day'));
        await open();
        component['reason'].set('Dies Academicus');
        fixture.detectChanges();
        expect((query('holiday-submit').nativeElement as HTMLButtonElement).disabled).toBe(false);

        // The picker keeps its last good value, so without this the nonsense on screen would save the old date.
        query('holiday-start').triggerEventHandler('inputValidityChange', false);
        fixture.detectChanges();

        expect((query('holiday-submit').nativeElement as HTMLButtonElement).disabled).toBe(true);
    });

    it('should let saving resume once the text parses again', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-04').startOf('day'));
        await open();
        component['reason'].set('Dies Academicus');
        query('holiday-end').triggerEventHandler('inputValidityChange', false);
        fixture.detectChanges();

        query('holiday-end').triggerEventHandler('inputValidityChange', true);
        fixture.detectChanges();

        expect((query('holiday-submit').nativeElement as HTMLButtonElement).disabled).toBe(false);
    });

    it('should not carry invalid text into the next holiday the dialog opens on', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-04').startOf('day'));
        await open();
        query('holiday-start').triggerEventHandler('inputValidityChange', false);
        fixture.componentRef.setInput('visible', false);
        fixture.detectChanges();

        fixture.componentRef.setInput('initialDay', dayjs('2025-12-20').startOf('day'));
        await open();
        component['reason'].set('Dies Academicus');
        fixture.detectChanges();

        expect((query('holiday-submit').nativeElement as HTMLButtonElement).disabled).toBe(false);
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
