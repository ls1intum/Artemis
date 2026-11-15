/**
 * Vitest tests for CleanupServiceComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';

import { CleanupServiceComponent } from 'app/admin/cleanup-service/cleanup-service.component';
import { CleanupOperation } from 'app/admin/cleanup-service/cleanup-operation.model';
import { CleanupConfiguration, CleanupServiceExecutionRecordDTO, DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const CONFIGURATION: CleanupConfiguration = {
    gradeRelevantRetentionYears: 5,
    gradeRelevantCoursesEndedBefore: dayjs('2021-03-04T00:00:00Z'),
    nonGradeRelevantRetentionYears: 1,
    nonGradeRelevantCoursesEndedBefore: dayjs('2025-03-04T00:00:00Z'),
    resetWarningGracePeriodDays: 30,
    coursesWarnedBefore: dayjs('2026-02-02T00:00:00Z'),
    oldFeedbackCutoffWeeks: 8,
    oldFeedbackCoursesEndedBefore: dayjs('2026-01-07T00:00:00Z'),
    oldSubmissionVersionsCutoffWeeks: 1,
    oldSubmissionVersionsCoursesEndedBefore: dayjs('2026-02-25T00:00:00Z'),
    notEnrolledUsersInactivityMonths: 6,
    usersInactiveBefore: dayjs('2025-09-04T00:00:00Z'),
    notEnrolledUsersWarningGracePeriodDays: 1,
    usersWarnedBefore: dayjs('2026-03-03T00:00:00Z'),
};

describe('CleanupServiceComponent', () => {
    let comp: CleanupServiceComponent;
    let fixture: ComponentFixture<CleanupServiceComponent>;
    let cleanupService: DataCleanupService;

    beforeEach(async () => {
        const mockCleanupService = {
            getLastExecutions: vi.fn().mockReturnValue(of(new HttpResponse<CleanupServiceExecutionRecordDTO[]>({ body: [] }))),
            getCleanupConfiguration: vi.fn().mockReturnValue(of(CONFIGURATION)),
        };

        await TestBed.configureTestingModule({
            imports: [CleanupServiceComponent],
            providers: [
                { provide: DataCleanupService, useValue: mockCleanupService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(CleanupServiceComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(CleanupServiceComponent);
        comp = fixture.componentInstance;
        cleanupService = TestBed.inject(DataCleanupService);
    });

    it('should load last executions on init', () => {
        const executionRecord: CleanupServiceExecutionRecordDTO[] = [{ executionDate: dayjs(), jobType: 'deleteOrphans' }];
        const response = new HttpResponse<CleanupServiceExecutionRecordDTO[]>({
            body: executionRecord,
        });

        vi.spyOn(cleanupService, 'getLastExecutions').mockReturnValue(of(response));

        comp.ngOnInit();

        expect(cleanupService.getLastExecutions).toHaveBeenCalledOnce();
        expect(comp.cleanupOperations()[0].lastExecuted).toEqual(dayjs(executionRecord[0].executionDate));
    });

    it('should match execution records by server job type, not array position', () => {
        // The server labels several jobs differently from the client operation names (client 'deleteOldRatedResults'
        // -> server 'deleteRatedResults', 'deleteOldSubmissionVersions' -> 'deleteSubmissionVersions'). A record must
        // be attributed to the operation whose serverJobTypeByName matches, never by index.
        const ratedDate = dayjs().subtract(1, 'day');
        const submissionDate = dayjs().subtract(2, 'days');
        const response = new HttpResponse<CleanupServiceExecutionRecordDTO[]>({
            body: [
                { executionDate: ratedDate, jobType: 'deleteRatedResults' },
                { executionDate: submissionDate, jobType: 'deleteSubmissionVersions' },
            ],
        });
        vi.spyOn(cleanupService, 'getLastExecutions').mockReturnValue(of(response));

        comp.ngOnInit();

        const operations = comp.cleanupOperations();
        expect(operations.find((operation) => operation.name === 'deleteOldRatedResults')?.lastExecuted).toEqual(dayjs(ratedDate));
        expect(operations.find((operation) => operation.name === 'deleteOldSubmissionVersions')?.lastExecuted).toEqual(dayjs(submissionDate));
        // The first operation ('deleteOrphans') must stay untouched even though records appeared first in the array.
        expect(operations.find((operation) => operation.name === 'deleteOrphans')?.lastExecuted).toBeUndefined();
    });

    it('should alert on a failed executions load', () => {
        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');
        vi.spyOn(cleanupService, 'getLastExecutions').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

        comp.ngOnInit();

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('should validate date ranges correctly', () => {
        const validOperation: CleanupOperation = {
            name: 'deleteOrphans',
            action: 'delete',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        };

        const invalidOperation: CleanupOperation = {
            name: 'deleteOrphans',
            action: 'delete',
            deleteFrom: dayjs(),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        };

        comp.validateDates(validOperation);
        comp.validateDates(invalidOperation);

        expect(validOperation.datesValid()).toBe(true);
        expect(invalidOperation.datesValid()).toBe(false);
    });

    it('should clear the model and invalidate the row when a date is cleared', () => {
        const operation: CleanupOperation = {
            name: 'deletePlagiarismComparisons',
            action: 'delete',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        };

        // Clearing a field must drop the stale date so a deletion cannot run against a range no longer shown.
        comp.onDeleteFromChange(operation, undefined);

        expect(operation.deleteFrom).toBeUndefined();
        expect(operation.datesValid()).toBe(false);
    });

    it('should set a new from-date and revalidate the row', () => {
        const operation: CleanupOperation = {
            name: 'deletePlagiarismComparisons',
            action: 'delete',
            deleteFrom: undefined,
            deleteTo: dayjs(),
            lastExecuted: undefined,
            datesValid: signal(false),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        };

        const newFrom = dayjs().subtract(6, 'months');
        comp.onDeleteFromChange(operation, newFrom);

        expect(operation.deleteFrom?.toISOString()).toBe(newFrom.toISOString());
        expect(operation.datesValid()).toBe(true);
    });

    it('should clear the model and invalidate the row when the to-date is cleared', () => {
        const operation: CleanupOperation = {
            name: 'deletePlagiarismComparisons',
            action: 'delete',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        };

        comp.onDeleteToChange(operation, undefined);

        expect(operation.deleteTo).toBeUndefined();
        expect(operation.datesValid()).toBe(false);
    });

    it('should set a new to-date and revalidate the row', () => {
        const operation: CleanupOperation = {
            name: 'deletePlagiarismComparisons',
            action: 'delete',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: undefined,
            lastExecuted: undefined,
            datesValid: signal(false),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        };

        const newTo = dayjs();
        comp.onDeleteToChange(operation, newTo);

        expect(operation.deleteTo?.toISOString()).toBe(newTo.toISOString());
        expect(operation.datesValid()).toBe(true);
    });

    it('should select the operation and show the modal when opened', () => {
        const operation = comp.cleanupOperations()[0];

        comp.openCleanupOperationModal(operation);

        expect(comp.selectedOperation()).toBe(operation);
        expect(comp.showCleanupModal()).toBe(true);
    });

    it('should expose the new data-privacy operations as age-based (no date range) and valid', () => {
        const operations = comp.cleanupOperations();
        const ageBasedNames = [
            'warnOldCoursesReset',
            'resetOldCourses',
            'deleteOldFeedback',
            'deleteOldCourseSubmissionVersions',
            'warnNotEnrolledUsers',
            'deleteNotEnrolledUsers',
            'deletePlagiarismCases',
        ];

        for (const name of ageBasedNames) {
            const operation = operations.find((candidate) => candidate.name === name);
            expect(operation).toBeDefined();
            expect(operation!.ageBased).toBe(true);
            // age-based operations have no admin-picked date range and are always executable
            expect(operation!.deleteFrom).toBeUndefined();
            expect(operation!.deleteTo).toBeUndefined();
            expect(operation!.datesValid()).toBe(true);
        }
    });

    it('should label every operation with the action it actually performs', () => {
        const actionByName = new Map(comp.cleanupOperations().map((operation) => [operation.name, operation.action]));

        // "Delete" is wrong for an operation that only emails a warning, or that resets a course while keeping it.
        expect(actionByName.get('warnOldCoursesReset')).toBe('warn');
        expect(actionByName.get('warnNotEnrolledUsers')).toBe('warn');
        expect(actionByName.get('resetOldCourses')).toBe('reset');
        for (const name of ['deleteOrphans', 'deleteOldFeedback', 'deleteNotEnrolledUsers', 'deletePlagiarismCases'] as const) {
            expect(actionByName.get(name)).toBe('delete');
        }
    });

    it('should load the effective cleanup configuration on init', () => {
        comp.ngOnInit();

        expect(cleanupService.getCleanupConfiguration).toHaveBeenCalledOnce();
        expect(comp.configuration()).toEqual(CONFIGURATION);
    });

    it('should alert on a failed configuration load', () => {
        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');
        vi.spyOn(cleanupService, 'getCleanupConfiguration').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

        comp.ngOnInit();

        expect(errorSpy).toHaveBeenCalledOnce();
        expect(comp.configuration()).toBeUndefined();
    });

    it('should flag a failed configuration load so the affected rows can say their scope is unknown', () => {
        const alertService = TestBed.inject(AlertService);
        vi.spyOn(alertService, 'error');
        vi.spyOn(cleanupService, 'getCleanupConfiguration').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

        comp.ngOnInit();

        expect(comp.configurationFailed()).toBe(true);
        expect(comp.descriptions()).toEqual({});
    });

    it('should describe an age-based operation with the cutoff the server would apply', () => {
        comp.ngOnInit();

        // MockTranslateService echoes the key, so the resolved period is the duration key the component picked.
        expect(comp.descriptions().deletePlagiarismCases).toEqual({
            cutoff: 'Mar 4, 2021',
            period: 'cleanupService.duration.years',
            secondaryCutoff: undefined,
            secondaryPeriod: undefined,
        });

        // The reset is measured from the warning, not from the course end, so it must quote the warning cutoff.
        expect(comp.descriptions().resetOldCourses?.cutoff).toBe('Feb 2, 2026');
        expect(comp.descriptions().resetOldCourses?.period).toBe('cleanupService.duration.days');
    });

    it('should describe both retention cutoffs of the old-course warning', () => {
        comp.ngOnInit();

        expect(comp.descriptions().warnOldCoursesReset).toEqual({
            cutoff: 'Mar 4, 2021',
            period: 'cleanupService.duration.years',
            secondaryCutoff: 'Mar 4, 2025',
            // A configured period of 1 must resolve to the singular key, otherwise the line reads "1 years".
            secondaryPeriod: 'cleanupService.duration.year',
        });
    });

    it('should pick the singular duration key for every period configured as one', () => {
        comp.ngOnInit();

        expect(comp.descriptions().deleteOldCourseSubmissionVersions?.period).toBe('cleanupService.duration.week');
        expect(comp.descriptions().deleteNotEnrolledUsers?.period).toBe('cleanupService.duration.day');
    });

    it('should not quote a second cutoff for the not-enrolled user deletion', () => {
        comp.ngOnInit();

        // Phase 2 compares each user's last login against their own warning date, which no global cutoff can express.
        expect(comp.descriptions().deleteNotEnrolledUsers?.secondaryCutoff).toBeUndefined();
        expect(comp.descriptions().deleteNotEnrolledUsers?.secondaryPeriod).toBeUndefined();
    });

    it('should describe nothing until the configuration has loaded', () => {
        // Rendering a cutoff before the server answered would state a date the operation does not actually use.
        expect(comp.descriptions()).toEqual({});
        expect(comp.configurationFailed()).toBe(false);
    });

    it('should mark only the cutoff-quoting operations as needing the configuration', () => {
        comp.ngOnInit();
        const quoting = comp
            .cleanupOperations()
            .map((operation) => operation.name)
            .filter((name) => comp.descriptions()[name] !== undefined);

        // The four date-range operations state their scope through their pickers, and orphans have no time bound.
        expect(quoting).toEqual([
            'warnOldCoursesReset',
            'resetOldCourses',
            'deleteOldFeedback',
            'deleteOldCourseSubmissionVersions',
            'warnNotEnrolledUsers',
            'deleteNotEnrolledUsers',
            'deletePlagiarismCases',
        ]);
    });

    it('should supply every placeholder its description string interpolates', async () => {
        // The rendered line must never contain a hole. This pins the component and the translations together, so a
        // string that starts quoting a cutoff, or an operation added without one, fails here instead of in the UI.
        comp.ngOnInit();
        const translations = (await import('../../../i18n/en/cleanupService.json')).default.cleanupService;

        for (const operation of comp.cleanupOperations()) {
            const template: string = translations.description[operation.name];
            expect(template, `missing cleanupService.description.${operation.name}`).toBeDefined();

            const placeholders = [...template.matchAll(/{{\s*(\w+)\s*}}/g)].map((match) => match[1]);
            const description = comp.descriptions()[operation.name];
            for (const placeholder of placeholders) {
                expect(description?.[placeholder as keyof typeof description], `${operation.name} does not supply {{${placeholder}}}`).toBeTruthy();
            }
            // Conversely, every value supplied must be one the string actually interpolates, so that dropping a
            // {{period}} from a translation is caught too, not just dropping the value behind it.
            if (description) {
                const supplied = Object.entries(description)
                    .filter(([, value]) => value !== undefined)
                    .map(([key]) => key);
                expect(new Set(placeholders), `${operation.name} supplies values that do not match its placeholders`).toEqual(new Set(supplied));
            }
        }
    });
});
