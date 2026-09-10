import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideArtemisTumUiTranslator } from 'app/shared-ui/tum-ui-integration/artemis-tum-ui-translator';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Course } from 'app/course/shared/entities/course.model';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';
import { TutorialGroupFreePeriodService } from 'app/tutorialgroup/manage/service/tutorial-group-free-period.service';
import { TutorialGroupHolidaysComponent } from 'app/tutorialgroup/manage/holidays/tutorial-group-holidays.component';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';

const TIME_ZONE = 'Europe/Berlin';

const course = { id: 42, title: 'Introduction to Programming', timeZone: TIME_ZONE, isAtLeastInstructor: true } as Course;

/** The configuration DTO the service answers with, holding one whole-day holiday on 17 December. */
const configurationDto = {
    id: 7,
    course: { id: 42 },
    tutorialPeriodStartInclusive: '2025-10-01T00:00:00Z',
    tutorialPeriodEndInclusive: '2026-02-01T00:00:00Z',
    tutorialGroupFreePeriods: [{ id: 11, start: '2025-12-16T23:00:00Z', end: '2025-12-17T22:59:00Z', reason: 'Christmas holidays' }],
};

describe('TutorialGroupHolidaysComponent', () => {
    let fixture: ComponentFixture<TutorialGroupHolidaysComponent>;
    let component: TutorialGroupHolidaysComponent;
    let freePeriodService: TutorialGroupFreePeriodService;
    let configurationService: TutorialGroupsConfigurationService;

    beforeEach(async () => {
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

        configurationService = TestBed.inject(TutorialGroupsConfigurationService);
        freePeriodService = TestBed.inject(TutorialGroupFreePeriodService);

        vi.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: configurationDto as never })));
        vi.spyOn(freePeriodService, 'getSessionCounts').mockReturnValue(of([{ date: '2025-12-17', count: 7 }]));

        fixture = TestBed.createComponent(TutorialGroupHolidaysComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('should load the holidays of the course', () => {
        expect(component['freePeriods']()).toHaveLength(1);
        expect(component['holidays']()[0].reason).toBe('Christmas holidays');
    });

    it('should ask for session counts covering the whole grid, not just the month', () => {
        const [, from, to] = vi.mocked(freePeriodService.getSessionCounts).mock.calls[0];

        // The December 2025 grid starts on 1 December and runs to 4 January, both of which need their counts.
        expect(from.format('YYYY-MM-DD')).toBe(component['displayedMonth']().startOf('month').startOf('isoWeek').format('YYYY-MM-DD'));
        expect(to.isAfter(component['displayedMonth']().endOf('month').subtract(1, 'day'))).toBe(true);
    });

    it('should reload the session counts when the month changes', () => {
        vi.mocked(freePeriodService.getSessionCounts).mockClear();

        component['onMonthChange'](dayjs('2026-01-01').startOf('month'));

        expect(freePeriodService.getSessionCounts).toHaveBeenCalledOnce();
        expect(component['displayedMonth']().format('YYYY-MM')).toBe('2026-01');
    });

    it('should store the span exactly as the dialog produced it', () => {
        const create = vi.spyOn(freePeriodService, 'create').mockReturnValue(of(new HttpResponse({ body: new TutorialGroupFreePeriod() })));

        component['onSave']({ start: dayjs('2025-12-04T00:00'), end: dayjs('2025-12-04T23:59'), reason: 'Dies Academicus' });

        const payload = create.mock.calls[0][2];

        expect(dayjs(payload.startDate).format('YYYY-MM-DD HH:mm')).toBe('2025-12-04 00:00');
        expect(dayjs(payload.endDate).format('YYYY-MM-DD HH:mm')).toBe('2025-12-04 23:59');
        expect(payload.reason).toBe('Dies Academicus');
    });

    it('should store a holiday covering two weeks as a single period', () => {
        const create = vi.spyOn(freePeriodService, 'create').mockReturnValue(of(new HttpResponse({ body: new TutorialGroupFreePeriod() })));

        component['onSave']({ start: dayjs('2025-12-22T00:00'), end: dayjs('2026-01-05T23:59'), reason: 'Christmas holidays' });

        expect(create).toHaveBeenCalledOnce();
        expect(dayjs(create.mock.calls[0][2].endDate).format('YYYY-MM-DD')).toBe('2026-01-05');
    });

    it('should store the chosen times when the holiday only covers part of a day', () => {
        const create = vi.spyOn(freePeriodService, 'create').mockReturnValue(of(new HttpResponse({ body: new TutorialGroupFreePeriod() })));

        component['onSave']({ start: dayjs('2025-12-04T09:15'), end: dayjs('2025-12-04T13:45'), reason: 'Dies Academicus' });

        const payload = create.mock.calls[0][2];

        expect(dayjs(payload.startDate).format('HH:mm')).toBe('09:15');
        expect(dayjs(payload.endDate).format('HH:mm')).toBe('13:45');
    });

    it('should update rather than create when a holiday is being edited', () => {
        const update = vi.spyOn(freePeriodService, 'update').mockReturnValue(of(new HttpResponse({ body: new TutorialGroupFreePeriod() })));
        const create = vi.spyOn(freePeriodService, 'create');
        component['openEditDialog'](component['holidays']()[0]);

        component['onSave']({ start: dayjs('2025-12-17T00:00'), end: dayjs('2025-12-17T23:59'), reason: 'Christmas holidays' });

        expect(update).toHaveBeenCalledOnce();
        expect(update.mock.calls[0][2]).toBe(11);
        expect(create).not.toHaveBeenCalled();
    });

    it('should count the sessions of the span the dialog is showing, not just of the loaded month', () => {
        vi.useFakeTimers();
        vi.mocked(freePeriodService.getSessionCounts).mockClear();
        vi.mocked(freePeriodService.getSessionCounts).mockReturnValue(
            of([
                { date: '2025-12-22', count: 4 },
                { date: '2025-12-23', count: 3 },
            ]),
        );

        component['onDialogSpanChange']({ start: dayjs('2025-12-22T00:00'), end: dayjs('2026-01-05T23:59') });
        vi.advanceTimersByTime(500);

        // The span is asked for directly, so a holiday running past the displayed month is still counted in full.
        const [, from, to] = vi.mocked(freePeriodService.getSessionCounts).mock.calls[0];
        expect(from.format('YYYY-MM-DD')).toBe('2025-12-22');
        expect(to.format('YYYY-MM-DD')).toBe('2026-01-05');
        expect(component['dialogSessionCount']()).toBe(7);
    });

    it('should ask for the count once when the reader moves through several dates', () => {
        vi.useFakeTimers();
        vi.mocked(freePeriodService.getSessionCounts).mockClear();
        vi.mocked(freePeriodService.getSessionCounts).mockReturnValue(of([{ date: '2025-12-24', count: 2 }]));

        // Typing a date emits per keystroke, so only the span the reader settled on should reach the server.
        for (const day of ['2025-12-22', '2025-12-23', '2025-12-24']) {
            component['onDialogSpanChange']({ start: dayjs(`${day}T00:00`), end: dayjs(`${day}T23:59`) });
            vi.advanceTimersByTime(50);
        }
        vi.advanceTimersByTime(500);

        expect(freePeriodService.getSessionCounts).toHaveBeenCalledOnce();
        expect(vi.mocked(freePeriodService.getSessionCounts).mock.calls[0][1].format('YYYY-MM-DD')).toBe('2025-12-24');
    });

    it('should keep a holiday covering several days as one entry and mark each of its days', () => {
        const multiDay = new TutorialGroupFreePeriod();
        multiDay.id = 12;
        multiDay.start = dayjs.utc('2025-12-16T23:00:00');
        multiDay.end = dayjs.utc('2025-12-19T22:59:00');
        multiDay.reason = 'Christmas holidays';
        component['freePeriods'].set([multiDay]);

        expect(component['holidays']()).toHaveLength(1);
        expect(component['holidays']()[0].dayCount).toBe(3);
        expect([...component['holidaysByDay']().keys()]).toEqual(['2025-12-17', '2025-12-18', '2025-12-19']);
    });
});
