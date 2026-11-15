/**
 * Vitest tests for DataCleanupService.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { Observable } from 'rxjs';

import {
    CleanupConfiguration,
    CleanupCount,
    DataCleanupService,
    NonLatestNonRatedResultsCleanupCountDTO,
    NonLatestRatedResultsCleanupCountDTO,
    OrphanCleanupCountDTO,
    PlagiarismComparisonCleanupCountDTO,
    SubmissionVersionsCleanupCountDTO,
} from 'app/admin/cleanup-service/data-cleanup.service';
import { CleanupConfiguration as CleanupConfigurationResponse } from 'app/openapi/model/cleanup-configuration';
import { CleanupServiceExecutionRecord } from 'app/openapi/model/cleanup-service-execution-record';
import { NotEnrolledUsersCleanupCount } from 'app/openapi/model/not-enrolled-users-cleanup-count';
import { OldCoursesCleanupCount } from 'app/openapi/model/old-courses-cleanup-count';
import { OldFeedbackCleanupCount } from 'app/openapi/model/old-feedback-cleanup-count';
import { PlagiarismCasesCleanupCount } from 'app/openapi/model/plagiarism-cases-cleanup-count';

describe('DataCleanupService', () => {
    let service: DataCleanupService;
    let httpMock: HttpTestingController;

    const executionDate = '2024-03-09T13:06:36.100Z';
    const deleteFrom = '2024-03-07T13:06:36.100Z';
    const deleteTo = '2024-03-08T13:06:36.100Z';
    const mockExecutionRecord: CleanupServiceExecutionRecord = { executionDate, jobType: 'deleteOrphans' };
    const mockConfiguration: CleanupConfigurationResponse = {
        gradeRelevantRetentionYears: 5,
        gradeRelevantCoursesEndedBefore: '2021-03-04T00:00:00Z',
        nonGradeRelevantRetentionYears: 1,
        nonGradeRelevantCoursesEndedBefore: '2025-03-04T00:00:00Z',
        resetWarningGracePeriodDays: 30,
        coursesWarnedBefore: '2026-02-02T00:00:00Z',
        oldFeedbackCutoffWeeks: 8,
        oldFeedbackCoursesEndedBefore: '2026-01-07T00:00:00Z',
        oldSubmissionVersionsCutoffWeeks: 8,
        oldSubmissionVersionsCoursesEndedBefore: '2026-01-07T00:00:00Z',
        notEnrolledUsersInactivityMonths: 6,
        usersInactiveBefore: '2025-09-04T00:00:00Z',
        notEnrolledUsersWarningGracePeriodDays: 30,
        usersWarnedBefore: '2026-02-02T00:00:00Z',
    };
    const mockOrphanCount: OrphanCleanupCountDTO = {
        orphanStudentScore: 1,
        orphanTeamScore: 1,
        orphanFeedbackForOrphanResults: 0,
        orphanLongFeedbackTextForOrphanResults: 0,
        orphanTextBlockForOrphanResults: 0,
        orphanRating: 2,
        orphanResultsWithoutParticipation: 0,
        orphanFeedbackMessage: 0,
    };

    const mockPlagiarismCount: PlagiarismComparisonCleanupCountDTO = {
        plagiarismComparison: 3,
        plagiarismElements: 1,
        plagiarismSubmissions: 1,
        plagiarismMatches: 0,
    };

    const mockNonRatedResultsCount: NonLatestNonRatedResultsCleanupCountDTO = {
        longFeedbackText: 1,
        textBlock: 2,
        feedback: 1,
    };

    const mockRatedResultsCount: NonLatestRatedResultsCleanupCountDTO = {
        longFeedbackText: 2,
        textBlock: 3,
        feedback: 2,
    };

    const mockSubmissionVersionsCount: SubmissionVersionsCleanupCountDTO = {
        submissionVersions: 8,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [DataCleanupService, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(DataCleanupService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    function expectCleanupRequest(method: 'GET' | 'DELETE' | 'POST', path: string, query: [string, string][] = []): TestRequest {
        const pathname = `/api/admin/cleanup/${path}`;
        const request = httpMock.expectOne((req) => new URL(req.urlWithParams, 'http://localhost').pathname === pathname);
        const url = new URL(request.request.urlWithParams, 'http://localhost');
        expect(request.request.method).toBe(method);
        expect(url.searchParams.size).toBe(query.length);
        for (const [name, value] of query) {
            expect(url.searchParams.get(name)).toBe(value);
        }
        expect(request.request.body).toBeNull();
        return request;
    }

    it.each([
        ['deleteOrphans', 'DELETE', 'orphans', 'deleteOrphans'],
        ['warnOldCoursesReset', 'POST', 'old-courses/warn', 'warnOldCoursesReset'],
        ['resetOldCourses', 'DELETE', 'old-courses/reset', 'resetOldCourses'],
        ['deleteOldFeedback', 'DELETE', 'old-feedback', 'deleteFeedback'],
        ['deleteOldCourseSubmissionVersions', 'DELETE', 'old-course-submission-versions', 'deleteOldCourseSubmissionVersions'],
        ['warnNotEnrolledUsers', 'POST', 'not-enrolled-users/warn', 'warnNotEnrolledUsers'],
        ['deleteNotEnrolledUsers', 'DELETE', 'not-enrolled-users', 'deleteNotEnrolledUsers'],
        ['deletePlagiarismCases', 'DELETE', 'plagiarism-cases', 'deletePlagiarismCases'],
    ] satisfies [keyof DataCleanupService, 'DELETE' | 'POST', string, string][])('should send %s without query parameters', (action, method, path, jobType) => {
        const body: CleanupServiceExecutionRecord = { executionDate, jobType };
        service[action]().subscribe((record) => {
            expect(record.jobType).toBe(jobType);
            expect(dayjs.isDayjs(record.executionDate)).toBe(true);
            expect(record.executionDate?.toISOString()).toBe(executionDate);
        });
        expectCleanupRequest(method, path).flush(body);
    });

    it.each([
        ['deletePlagiarismComparisons', 'plagiarism-comparisons', 'deletePlagiarismComparisons'],
        ['deleteNonRatedResults', 'non-rated-results', 'deleteNonRatedResults'],
        ['deleteOldRatedResults', 'old-rated-results', 'deleteRatedResults'],
        ['deleteOldSubmissionVersions', 'old-submission-versions', 'deleteSubmissionVersions'],
    ] satisfies [keyof DataCleanupService, string, string][])('should send %s with the exact UTC range', (action, path, jobType) => {
        const body: CleanupServiceExecutionRecord = { executionDate, jobType };
        service[action](dayjs('2024-03-07T15:06:36.100+02:00'), dayjs(deleteTo)).subscribe((record) => {
            expect(record.jobType).toBe(jobType);
            expect(dayjs.isDayjs(record.executionDate)).toBe(true);
            expect(record.executionDate?.toISOString()).toBe(executionDate);
        });
        expectCleanupRequest('DELETE', path, [
            ['deleteFrom', deleteFrom],
            ['deleteTo', deleteTo],
        ]).flush(body);
    });

    it('should map last execution dates and preserve an omitted date', () => {
        const neverRun: CleanupServiceExecutionRecord = { jobType: 'warnNotEnrolledUsers' };
        service.getLastExecutions().subscribe((records) => {
            expect(records.map((record) => record.jobType)).toEqual(['deleteOrphans', 'warnNotEnrolledUsers']);
            expect(dayjs.isDayjs(records[0].executionDate)).toBe(true);
            expect(records[0].executionDate?.toISOString()).toBe(executionDate);
            expect(records[1].executionDate).toBeUndefined();
        });
        expectCleanupRequest('GET', 'last-executions').flush([mockExecutionRecord, neverRun]);
    });

    it.each([
        ['countPlagiarismComparisons', 'plagiarism-comparisons/count', mockPlagiarismCount],
        ['countNonRatedResults', 'non-rated-results/count', mockNonRatedResultsCount],
        ['countOldRatedResults', 'old-rated-results/count', mockRatedResultsCount],
        ['countOldSubmissionVersions', 'old-submission-versions/count', mockSubmissionVersionsCount],
    ] satisfies [keyof DataCleanupService, string, Record<string, number>][])('should send %s with the exact UTC range', (action, path, body) => {
        const result: Observable<CleanupCount> = service[action](dayjs(deleteFrom), dayjs(deleteTo));
        result.subscribe((counts) => expect(counts).toEqual(body));
        expectCleanupRequest('GET', path, [
            ['deleteFrom', deleteFrom],
            ['deleteTo', deleteTo],
        ]).flush(body);
    });

    it.each([
        ['countOrphans', 'orphans/count', mockOrphanCount],
        ['countOldCoursesResetWarning', 'old-courses/warn/count', { courses: 2 } satisfies OldCoursesCleanupCount],
        ['countOldCoursesReset', 'old-courses/reset/count', { courses: 1 } satisfies OldCoursesCleanupCount],
        ['countOldFeedback', 'old-feedback/count', { longFeedbackText: 1, textBlock: 2, feedback: 3 } satisfies OldFeedbackCleanupCount],
        ['countOldCourseSubmissionVersions', 'old-course-submission-versions/count', mockSubmissionVersionsCount],
        ['countNotEnrolledUsersWarning', 'not-enrolled-users/warn/count', { users: 7, blockedUsers: 0 } satisfies NotEnrolledUsersCleanupCount],
        ['countNotEnrolledUsers', 'not-enrolled-users/count', { users: 5, blockedUsers: 0 } satisfies NotEnrolledUsersCleanupCount],
        ['countPlagiarismCases', 'plagiarism-cases/count', { plagiarismCases: 3 } satisfies PlagiarismCasesCleanupCount],
    ] satisfies [keyof DataCleanupService, string, object][])('should send %s and preserve the counts and their key order', (action, path, body) => {
        const result: Observable<CleanupCount> = service[action]();
        result.subscribe((counts) => {
            expect(counts).toEqual(body);
            expect(Object.keys(counts)).toEqual(Object.keys(body));
        });
        expectCleanupRequest('GET', path).flush(body);
    });

    it.each([
        [403, 'Forbidden'],
        [500, 'Internal Server Error'],
    ])('should propagate HTTP %s from the generated client', (status, statusText) => {
        let error: HttpErrorResponse | undefined;
        service.deleteOrphans().subscribe({ error: (thrown: HttpErrorResponse) => (error = thrown) });
        expectCleanupRequest('DELETE', 'orphans').flush(null, { status, statusText });
        expect(error).toBeInstanceOf(HttpErrorResponse);
        expect(error?.status).toBe(status);
    });

    it('should report an invalid dayjs range as an Observable error without a request', () => {
        const request = service.deletePlagiarismComparisons(dayjs('invalid date'), dayjs(deleteTo));
        let error: Error | undefined;
        request.subscribe({ error: (thrown: Error) => (error = thrown) });
        expect(error?.message).toBe('The cleanup date range contains an invalid date.');
        httpMock.expectNone(() => true);
    });

    it('should send GET request for the cleanup configuration and parse the cutoffs as dates', () => {
        let configuration: CleanupConfiguration | undefined;
        service.getCleanupConfiguration().subscribe((response) => (configuration = response));

        expectCleanupRequest('GET', 'configuration').flush(mockConfiguration);

        expect(configuration?.gradeRelevantRetentionYears).toBe(5);
        // The cutoffs arrive as ISO strings and must reach the component as dayjs objects, ready to format.
        expect(dayjs.isDayjs(configuration?.gradeRelevantCoursesEndedBefore)).toBe(true);
        expect(configuration?.gradeRelevantCoursesEndedBefore.toISOString()).toBe(dayjs('2021-03-04T00:00:00Z').toISOString());
        expect(dayjs.isDayjs(configuration?.usersWarnedBefore)).toBe(true);
        expect(configuration?.usersWarnedBefore.toISOString()).toBe(dayjs('2026-02-02T00:00:00Z').toISOString());
    });

    it('should reject a cleanup configuration with a missing cutoff instead of defaulting it to now', () => {
        // dayjs(undefined) is today, which on this page would read as a plausible but entirely wrong cutoff.
        let error: Error | undefined;
        service.getCleanupConfiguration().subscribe({ error: (thrown: Error) => (error = thrown) });

        const { gradeRelevantCoursesEndedBefore: _omitted, ...missingCutoff } = mockConfiguration;
        expectCleanupRequest('GET', 'configuration').flush(missingCutoff);

        expect(error?.message).toBe('The cleanup configuration has no usable cutoff: received undefined');
    });

    it.each([
        // dayjs normalizes an out-of-range day to the next month with isValid() still true, which would read as a
        // plausible but wrong cutoff on the one page whose purpose is stating exact dates.
        ['2024-02-30T00:00:00Z', 'an out-of-range day'],
        ['2024-13-01T00:00:00Z', 'an out-of-range month'],
        ['2026-02-02', 'a date without a time'],
        ['not a date', 'unparseable text'],
    ])('should reject %s in the cleanup configuration (%s)', (cutoff) => {
        let error: Error | undefined;
        service.getCleanupConfiguration().subscribe({ error: (thrown: Error) => (error = thrown) });

        expectCleanupRequest('GET', 'configuration').flush({ ...mockConfiguration, gradeRelevantCoursesEndedBefore: cutoff });

        expect(error?.message).toBe(`The cleanup configuration has no usable cutoff: received ${cutoff}`);
    });

    it('should accept an offset cutoff, which is how the server serializes a ZonedDateTime', () => {
        let configuration: CleanupConfiguration | undefined;
        service.getCleanupConfiguration().subscribe((response) => (configuration = response));

        expectCleanupRequest('GET', 'configuration').flush({ ...mockConfiguration, gradeRelevantCoursesEndedBefore: '2021-03-04T12:30:45.123+02:00' });

        expect(configuration?.gradeRelevantCoursesEndedBefore.toISOString()).toBe(dayjs('2021-03-04T12:30:45.123+02:00').toISOString());
    });
});
