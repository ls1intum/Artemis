import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { By } from '@angular/platform-browser';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideArtemisTumUiTranslator } from 'app/shared-ui/tum-ui-integration/artemis-tum-ui-translator';
import { TumUiConfirmationService } from '@tumaet/ui-angular';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { Subject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Course } from 'app/course/shared/entities/course.model';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';
import { TutorialGroupFreePeriodService } from 'app/tutorialgroup/manage/service/tutorial-group-free-period.service';
import { TutorialGroupHolidaysComponent } from 'app/tutorialgroup/manage/holidays/tutorial-group-holidays.component';

const TIME_ZONE = 'Europe/Berlin';

/** Pinned, so "today", the month the calendar opens on and what counts as upcoming are all fixed. */
const TODAY = new Date('2025-12-10T09:00:00Z');

const course = { id: 42, title: 'Introduction to Programming', timeZone: TIME_ZONE, isAtLeastInstructor: true } as Course;

/**
 * What the configuration service answers with. Read in the course's zone the first holiday is 17 December 00:00 to
 * 23:59 and the second is 1 December, which is past and so hidden by the filter the list opens on.
 */
const configurationDto = {
    id: 7,
    course: { id: 42 },
    tutorialPeriodStartInclusive: '2025-10-01T00:00:00Z',
    tutorialPeriodEndInclusive: '2026-02-01T00:00:00Z',
    tutorialGroupFreePeriods: [
        { id: 11, start: '2025-12-16T23:00:00Z', end: '2025-12-17T22:59:00Z', reason: 'Christmas holidays' },
        { id: 12, start: '2025-11-30T23:00:00Z', end: '2025-12-01T22:59:00Z', reason: 'Past holiday' },
    ],
};

describe('TutorialGroupHolidaysComponent', () => {
    let fixture: ComponentFixture<TutorialGroupHolidaysComponent>;
    let component: TutorialGroupHolidaysComponent;
    let freePeriodService: TutorialGroupFreePeriodService;
    let confirmationService: TumUiConfirmationService;

    beforeEach(async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        vi.setSystemTime(TODAY);

        await TestBed.configureTestingModule({
            imports: [TutorialGroupHolidaysComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideArtemisTumUiTranslator(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: { data: of({ course }) } },
                CourseTitleBarService,
            ],
        }).compileComponents();

        const configurationService = TestBed.inject(TutorialGroupsConfigurationService);
        freePeriodService = TestBed.inject(TutorialGroupFreePeriodService);

        vi.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: configurationDto as never })));
        vi.spyOn(freePeriodService, 'getSessionCounts').mockReturnValue(of([{ date: '2025-12-17', count: 7 }]));
        vi.spyOn(freePeriodService, 'getOverlappingSessionCount').mockReturnValue(of(7));
        vi.spyOn(freePeriodService, 'getSessionCountsPerFreePeriod').mockReturnValue(of([{ freePeriodId: 11, count: 7 }]));

        fixture = TestBed.createComponent(TutorialGroupHolidaysComponent);
        component = fixture.componentInstance;
        confirmationService = fixture.debugElement.injector.get(TumUiConfirmationService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    /** The dialog fills its form in an effect, so its fields settle a tick after it opens. */
    async function settle(): Promise<void> {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    }

    const queryAll = (testId: string) => fixture.debugElement.queryAll(By.css(`[data-testid="${testId}"]`));
    const query = (testId: string) => fixture.debugElement.query(By.css(`[data-testid="${testId}"]`));

    it('should show a loaded holiday on its day in the calendar and once in the list', () => {
        expect(fixture.debugElement.query(By.css('[data-day="2025-12-17"][data-has-holiday]'))).not.toBeNull();
        // Only the upcoming one: the list opens on that filter and the 1 December holiday is past.
        expect(queryAll('holiday-list-item')).toHaveLength(1);
        expect(query('holiday-list-item').nativeElement.textContent).toContain('Christmas holidays');
    });

    it('should show the past holiday once the filter is switched to all', () => {
        fixture.debugElement.query(By.css('[data-filter="all"]')).nativeElement.click();
        fixture.detectChanges();

        expect(queryAll('holiday-list-item')).toHaveLength(2);
        expect(fixture.nativeElement.textContent).toContain('Past holiday');
    });

    it('should let the holiday rather than the session count speak for a day that has one', () => {
        expect(fixture.debugElement.query(By.css('[data-day="2025-12-17"] [data-testid="holiday-calendar-sessions"]'))).toBeNull();
    });

    it('should create the holiday the reader filled in when an empty day is clicked', async () => {
        const create = vi.spyOn(freePeriodService, 'create').mockReturnValue(of(new HttpResponse({ body: new TutorialGroupFreePeriod() })));

        fixture.debugElement.query(By.css('[data-day="2025-12-04"]')).nativeElement.click();
        await settle();

        const reason = query('holiday-reason').nativeElement as HTMLInputElement;
        reason.value = 'Dies Academicus';
        reason.dispatchEvent(new Event('input'));
        await settle();
        query('holiday-submit').nativeElement.click();

        // The whole path: the calendar prefilled the day, the dialog produced the span, the page mapped it to a request.
        expect(create).toHaveBeenCalledOnce();
        const [courseId, configurationId, payload] = create.mock.calls[0];
        expect([courseId, configurationId]).toEqual([42, 7]);
        expect(dayjs(payload.startDate).format('YYYY-MM-DD HH:mm')).toBe('2025-12-04 00:00');
        expect(dayjs(payload.endDate).format('YYYY-MM-DD HH:mm')).toBe('2025-12-04 23:59');
        expect(payload.reason).toBe('Dies Academicus');
    });

    it('should open the existing holiday when its bar in the calendar is clicked', async () => {
        // By id rather than by position: December also carries the past holiday, whose bar comes first.
        fixture.debugElement.query(By.css('[data-testid="holiday-calendar-event"][data-holiday-id="11"]')).nativeElement.click();
        await settle();

        expect((query('holiday-reason').nativeElement as HTMLInputElement).value).toBe('Christmas holidays');
    });

    it('should offer a blank holiday on a day that already carries one, so a second one is reachable', async () => {
        // Two holidays on one day are allowed as long as their times do not overlap, so the day has to stay clickable.
        fixture.debugElement.query(By.css('[data-day="2025-12-17"]')).nativeElement.click();
        await settle();

        expect((query('holiday-reason').nativeElement as HTMLInputElement).value).toBe('');
    });

    it('should update the holiday being edited instead of creating another', async () => {
        const update = vi.spyOn(freePeriodService, 'update').mockReturnValue(of(new HttpResponse({ body: new TutorialGroupFreePeriod() })));
        const create = vi.spyOn(freePeriodService, 'create');

        query('holiday-edit').nativeElement.click();
        await settle();
        query('holiday-submit').nativeElement.click();

        expect(update).toHaveBeenCalledOnce();
        expect(update.mock.calls[0][2]).toBe(11);
        expect(create).not.toHaveBeenCalled();
    });

    it('should confirm before deleting, and then delete the holiday the reader chose', () => {
        const confirm = vi.spyOn(confirmationService, 'confirm');
        const remove = vi.spyOn(freePeriodService, 'delete').mockReturnValue(of(new HttpResponse<void>()));

        query('holiday-delete').nativeElement.click();

        expect(confirm).toHaveBeenCalledOnce();
        expect(remove).not.toHaveBeenCalled();

        // Accepting the confirmation is what actually deletes it.
        confirm.mock.calls[0][0].accept();

        expect(remove).toHaveBeenCalledWith(42, 7, 11);
    });

    it('should ask for session counts covering the whole grid, not just the month', () => {
        vi.mocked(freePeriodService.getSessionCounts).mockClear();

        // January 2026 starts on a Thursday and ends on a Saturday, so both ends of its grid fall outside the month -
        // which December, starting on a Monday, would not have shown.
        query('holiday-calendar-next').nativeElement.click();

        const [, from, to] = vi.mocked(freePeriodService.getSessionCounts).mock.calls[0];
        expect(from.format('YYYY-MM-DD')).toBe('2025-12-29');
        expect(to.format('YYYY-MM-DD')).toBe('2026-02-01');
    });

    it('should move the calendar and reload the counts when the next month is requested', () => {
        vi.mocked(freePeriodService.getSessionCounts).mockClear();

        query('holiday-calendar-next').nativeElement.click();
        fixture.detectChanges();

        expect(query('holiday-calendar-month').nativeElement.textContent).toContain('2026');
        expect(freePeriodService.getSessionCounts).toHaveBeenCalledOnce();
    });

    it('should count what the span would actually cancel, by overlap rather than by whole days', () => {
        vi.mocked(freePeriodService.getOverlappingSessionCount).mockClear();
        vi.mocked(freePeriodService.getOverlappingSessionCount).mockReturnValue(of(2));

        // A holiday within one day: counting the day would credit it with sessions it does not touch.
        component['onDialogSpanChange']({ start: dayjs('2025-12-22T09:00'), end: dayjs('2025-12-22T10:00') });
        vi.advanceTimersByTime(500);

        const [, from, to] = vi.mocked(freePeriodService.getOverlappingSessionCount).mock.calls[0];
        expect(from.format('YYYY-MM-DDTHH:mm')).toBe('2025-12-22T09:00');
        expect(to.format('YYYY-MM-DDTHH:mm')).toBe('2025-12-22T10:00');
        expect(component['dialogSessionCount']()).toBe(2);
        // The per-day endpoint labels the calendar and must not be what the warning is built from.
        expect(freePeriodService.getSessionCounts).not.toHaveBeenCalledWith(42, expect.anything(), dayjs('2025-12-22T10:00'));
    });

    it('should show the counts of the month on screen when an older month answers last', () => {
        // Stepping quickly leaves several months in flight; without switching, the slowest answer would win.
        const december = new Subject<{ date: string; count: number }[]>();
        const january = new Subject<{ date: string; count: number }[]>();
        vi.mocked(freePeriodService.getSessionCounts).mockReturnValueOnce(december).mockReturnValueOnce(january);

        component['onMonthChange'](dayjs('2025-12-01'));
        component['onMonthChange'](dayjs('2026-01-01'));
        january.next([{ date: '2026-01-05', count: 3 }]);
        december.next([{ date: '2025-12-17', count: 9 }]);

        // December was left behind, so its late answer must not reach the calendar.
        expect(component['sessionCountsByDay']().get('2026-01-05')).toBe(3);
        expect(component['sessionCountsByDay']().has('2025-12-17')).toBe(false);
    });

    it('should show the newest per-holiday counts when an older reload answers last', () => {
        const first = new Subject<{ freePeriodId: number; count: number }[]>();
        const second = new Subject<{ freePeriodId: number; count: number }[]>();
        vi.mocked(freePeriodService.getSessionCountsPerFreePeriod).mockReturnValueOnce(first).mockReturnValueOnce(second);

        component['loadSessionCountsPerHoliday']();
        component['loadSessionCountsPerHoliday']();
        second.next([{ freePeriodId: 11, count: 2 }]);
        first.next([{ freePeriodId: 11, count: 99 }]);

        expect(component['sessionCountsByHoliday']().get(11)).toBe(2);
    });

    it('should keep counting spans after a failed request', () => {
        vi.mocked(freePeriodService.getOverlappingSessionCount).mockClear();
        vi.mocked(freePeriodService.getOverlappingSessionCount)
            .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })))
            .mockReturnValueOnce(of(4));

        component['onDialogSpanChange']({ start: dayjs('2025-12-22T09:00'), end: dayjs('2025-12-22T10:00') });
        vi.advanceTimersByTime(500);
        component['onDialogSpanChange']({ start: dayjs('2025-12-23T09:00'), end: dayjs('2025-12-23T10:00') });
        vi.advanceTimersByTime(500);

        // An error reaching the outer subscription would have closed the stream and ignored the second span.
        expect(freePeriodService.getOverlappingSessionCount).toHaveBeenCalledTimes(2);
        expect(component['dialogSessionCount']()).toBe(4);
    });

    it('should name the holiday being edited when counting, so reopening it does not report nothing', async () => {
        vi.mocked(freePeriodService.getOverlappingSessionCount).mockClear();

        query('holiday-edit').nativeElement.click();
        await settle();
        vi.advanceTimersByTime(500);

        expect(vi.mocked(freePeriodService.getOverlappingSessionCount).mock.calls.at(-1)![3]).toBe(11);
    });

    it('should ask for the count once when the reader moves through several dates', () => {
        vi.mocked(freePeriodService.getOverlappingSessionCount).mockClear();
        vi.mocked(freePeriodService.getOverlappingSessionCount).mockReturnValue(of(2));

        // Typing a date emits per keystroke, so only the span the reader settled on should reach the server.
        for (const day of ['2025-12-22', '2025-12-23', '2025-12-24']) {
            component['onDialogSpanChange']({ start: dayjs(`${day}T00:00`), end: dayjs(`${day}T23:59`) });
            vi.advanceTimersByTime(50);
        }
        vi.advanceTimersByTime(500);

        expect(freePeriodService.getOverlappingSessionCount).toHaveBeenCalledOnce();
        expect(vi.mocked(freePeriodService.getOverlappingSessionCount).mock.calls[0][1].format('YYYY-MM-DD')).toBe('2025-12-24');
    });
});
