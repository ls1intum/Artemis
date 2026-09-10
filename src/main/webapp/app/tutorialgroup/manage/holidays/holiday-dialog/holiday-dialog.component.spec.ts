import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideArtemisTumUiTranslator } from 'app/shared-ui/tum-ui-integration/artemis-tum-ui-translator';
import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { toOccurrences } from 'app/tutorialgroup/manage/holidays/holiday.model';
import { HolidayDialogComponent, HolidaySubmission } from 'app/tutorialgroup/manage/holidays/holiday-dialog/holiday-dialog.component';

const TIME_ZONE = 'Europe/Berlin';

function occurrenceOf(start: string, end: string, reason: string) {
    const freePeriod = new TutorialGroupFreePeriod();
    freePeriod.id = 3;
    freePeriod.start = dayjs.utc(start);
    freePeriod.end = dayjs.utc(end);
    freePeriod.reason = reason;
    return toOccurrences([freePeriod], TIME_ZONE)[0];
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

    /** ngModel writes the value to the input asynchronously, so the DOM is only settled after a stable tick. */
    async function open(): Promise<void> {
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    }

    it('should start a new holiday as a whole day, which is the common case', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-04').startOf('day'));
        await open();

        expect(fixture.debugElement.query(By.css('[data-testid="holiday-start-time"]'))).toBeNull();
    });

    it('should load the holiday being edited into the form', async () => {
        fixture.componentRef.setInput('holiday', occurrenceOf('2025-12-04T08:15:00', '2025-12-04T12:45:00', 'Dies Academicus'));
        await open();

        const reason = fixture.debugElement.query(By.css('[data-testid="holiday-reason"]')).nativeElement as HTMLInputElement;

        expect(reason.value).toBe('Dies Academicus');
        // A holiday stored as a span within a day opens with its times visible rather than as a whole day.
        expect(fixture.debugElement.query(By.css('[data-testid="holiday-start-time"]'))).not.toBeNull();
    });

    it('should reveal the time fields when whole day is switched off', async () => {
        await open();
        fixture.debugElement.query(By.css('[data-testid="holiday-whole-day"]')).triggerEventHandler('changed', false);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-testid="holiday-start-time"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="holiday-end-time"]'))).not.toBeNull();
    });

    it('should refuse to save without a reason, since the reason is what students are shown', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-04').startOf('day'));
        await open();

        const submit = fixture.debugElement.query(By.css('[data-testid="holiday-submit"]')).nativeElement as HTMLButtonElement;

        expect(submit.disabled).toBe(true);
    });

    it('should refuse to save a span that ends before it starts', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-04').startOf('day'));
        await open();
        fixture.debugElement.query(By.css('[data-testid="holiday-whole-day"]')).triggerEventHandler('changed', false);
        fixture.detectChanges();

        component['reason'].set('Dies Academicus');
        component['startTime'].set('14:00');
        component['endTime'].set('09:00');
        fixture.detectChanges();

        expect((fixture.debugElement.query(By.css('[data-testid="holiday-submit"]')).nativeElement as HTMLButtonElement).disabled).toBe(true);
        expect(fixture.debugElement.query(By.css('[data-testid="holiday-time-error"]'))).not.toBeNull();
    });

    it('should emit a whole-day submission without times', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-04').startOf('day'));
        await open();
        let submission: HolidaySubmission | undefined;
        component.save.subscribe((value) => (submission = value));

        component['reason'].set('Dies Academicus');
        fixture.detectChanges();
        fixture.debugElement.query(By.css('[data-testid="holiday-submit"]')).nativeElement.click();

        expect(submission?.wholeDay).toBe(true);
        expect(submission?.startTime).toBeUndefined();
        expect(submission?.day.format('YYYY-MM-DD')).toBe('2025-12-04');
    });

    it('should trim the reason, so trailing whitespace does not reach the students', async () => {
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-04').startOf('day'));
        await open();
        let submission: HolidaySubmission | undefined;
        component.save.subscribe((value) => (submission = value));

        component['reason'].set('  Dies Academicus  ');
        fixture.detectChanges();
        fixture.debugElement.query(By.css('[data-testid="holiday-submit"]')).nativeElement.click();

        expect(submission?.reason).toBe('Dies Academicus');
    });

    it('should reset a cancelled edit, so it does not leak into the next holiday created', async () => {
        fixture.componentRef.setInput('holiday', occurrenceOf('2025-12-04T08:15:00', '2025-12-04T12:45:00', 'Dies Academicus'));
        await open();
        fixture.componentRef.setInput('visible', false);
        fixture.detectChanges();

        fixture.componentRef.setInput('holiday', undefined);
        fixture.componentRef.setInput('initialDay', dayjs('2025-12-20').startOf('day'));
        await open();

        const reason = fixture.debugElement.query(By.css('[data-testid="holiday-reason"]')).nativeElement as HTMLInputElement;

        expect(reason.value).toBe('');
        expect(fixture.debugElement.query(By.css('[data-testid="holiday-start-time"]'))).toBeNull();
    });
});
