/**
 * Vitest tests for DataCleanupService.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';

import {
    CleanupServiceExecutionRecordDTO,
    DataCleanupService,
    NonLatestNonRatedResultsCleanupCountDTO,
    NonLatestRatedResultsCleanupCountDTO,
    OrphanCleanupCountDTO,
    PlagiarismComparisonCleanupCountDTO,
    SubmissionVersionsCleanupCountDTO,
} from 'app/core/admin/cleanup-service/data-cleanup.service';

describe('DataCleanupService', () => {
    setupTestBed({ zoneless: true });

    let service: DataCleanupService;
    let httpMock: HttpTestingController;

    const mockDate = dayjs();
    const mockExecutionRecord: CleanupServiceExecutionRecordDTO = { executionDate: mockDate, jobType: 'deleteOrphans' };
    const mockOrphanCount: OrphanCleanupCountDTO = {
        totalCount: 10,
        orphanFeedback: 2,
        orphanLongFeedbackText: 3,
        orphanTextBlock: 1,
        orphanStudentScore: 1,
        orphanTeamScore: 1,
        orphanFeedbackForOrphanResults: 0,
        orphanLongFeedbackTextForOrphanResults: 0,
        orphanTextBlockForOrphanResults: 0,
        orphanRating: 2,
        orphanResultsWithoutParticipation: 0,
    };

    const mockPlagiarismCount: PlagiarismComparisonCleanupCountDTO = {
        totalCount: 5,
        plagiarismComparison: 3,
        plagiarismElements: 1,
        plagiarismSubmissions: 1,
        plagiarismMatches: 0,
    };

    const mockNonRatedResultsCount: NonLatestNonRatedResultsCleanupCountDTO = {
        totalCount: 4,
        longFeedbackText: 1,
        textBlock: 2,
        feedback: 1,
    };

    const mockRatedResultsCount: NonLatestRatedResultsCleanupCountDTO = {
        totalCount: 7,
        longFeedbackText: 2,
        textBlock: 3,
        feedback: 2,
    };

    const mockSubmissionVersionsCount: SubmissionVersionsCleanupCountDTO = {
        totalCount: 8,
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

    it('should send DELETE request to delete orphans', () => {
        service.deleteOrphans().subscribe((res) => {
            expect(res.body).toEqual(mockExecutionRecord);
        });

        const req = httpMock.expectOne({ method: 'DELETE', url: 'api/core/admin/cleanup/orphans' });
        expect(req.request.method).toBe('DELETE');
        req.flush(mockExecutionRecord);
    });

    it('should send DELETE request to delete plagiarism comparisons with date range', () => {
        const deleteFrom = '2024-03-07T13:06:36.100Z';
        const deleteTo = '2024-03-08T13:06:36.100Z';

        service.deletePlagiarismComparisons(dayjs(deleteFrom), dayjs(deleteTo)).subscribe((res) => {
            expect(res.body).toEqual(mockExecutionRecord);
        });

        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `api/core/admin/cleanup/plagiarism-comparisons?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('DELETE');
        expect(req.request.params.get('deleteFrom')).toBe(deleteFrom);
        expect(req.request.params.get('deleteTo')).toBe(deleteTo);
        req.flush(mockExecutionRecord);
    });

    it('should send DELETE request to delete non-rated results with date range', () => {
        const deleteFrom = '2024-03-07T13:06:36.100Z';
        const deleteTo = '2024-03-08T13:06:36.100Z';

        service.deleteNonRatedResults(dayjs(deleteFrom), dayjs(deleteTo)).subscribe((res) => {
            expect(res.body).toEqual(mockExecutionRecord);
        });

        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `api/core/admin/cleanup/non-rated-results?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('DELETE');
        req.flush(mockExecutionRecord);
    });

    it('should send DELETE request to delete old rated results with date range', () => {
        const deleteFrom = '2024-03-07T13:06:36.100Z';
        const deleteTo = '2024-03-08T13:06:36.100Z';

        service.deleteOldRatedResults(dayjs(deleteFrom), dayjs(deleteTo)).subscribe((res) => {
            expect(res.body).toEqual(mockExecutionRecord);
        });

        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `api/core/admin/cleanup/old-rated-results?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('DELETE');
        req.flush(mockExecutionRecord);
    });

    it('should send GET request to get last executions', () => {
        const mockExecutionRecords = [mockExecutionRecord];

        service.getLastExecutions().subscribe((res) => {
            expect(res.body).toEqual(mockExecutionRecords);
        });

        const req = httpMock.expectOne({ method: 'GET', url: 'api/core/admin/cleanup/last-executions' });
        expect(req.request.method).toBe('GET');
        req.flush(mockExecutionRecords);
    });

    it('should send GET request to count orphans', () => {
        service.countOrphans().subscribe((res) => {
            expect(res.body).toEqual(mockOrphanCount);
        });

        const req = httpMock.expectOne({ method: 'GET', url: 'api/core/admin/cleanup/orphans/count' });
        expect(req.request.method).toBe('GET');
        req.flush(mockOrphanCount);
    });

    it('should send GET request to count plagiarism comparisons with date range', () => {
        const deleteFrom = '2024-03-07T13:06:36.100Z';
        const deleteTo = '2024-03-08T13:06:36.100Z';

        service.countPlagiarismComparisons(dayjs(deleteFrom), dayjs(deleteTo)).subscribe((res) => {
            expect(res.body).toEqual(mockPlagiarismCount);
        });

        const req = httpMock.expectOne({
            method: 'GET',
            url: `api/core/admin/cleanup/plagiarism-comparisons/count?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('GET');
        req.flush(mockPlagiarismCount);
    });

    it('should send GET request to count non-rated results with date range', () => {
        const deleteFrom = '2024-03-07T13:06:36.100Z';
        const deleteTo = '2024-03-08T13:06:36.100Z';

        service.countNonRatedResults(dayjs(deleteFrom), dayjs(deleteTo)).subscribe((res) => {
            expect(res.body).toEqual(mockNonRatedResultsCount);
        });

        const req = httpMock.expectOne({
            method: 'GET',
            url: `api/core/admin/cleanup/non-rated-results/count?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('GET');
        req.flush(mockNonRatedResultsCount);
    });

    it('should send GET request to count old rated results with date range', () => {
        const deleteFrom = '2024-03-07T13:06:36.100Z';
        const deleteTo = '2024-03-08T13:06:36.100Z';

        service.countOldRatedResults(dayjs(deleteFrom), dayjs(deleteTo)).subscribe((res) => {
            expect(res.body).toEqual(mockRatedResultsCount);
        });

        const req = httpMock.expectOne({
            method: 'GET',
            url: `api/core/admin/cleanup/old-rated-results/count?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('GET');
        req.flush(mockRatedResultsCount);
    });

    it('should send DELETE request to delete old submission versions with date range', () => {
        const deleteFrom = '2024-03-07T13:06:36.100Z';
        const deleteTo = '2024-03-08T13:06:36.100Z';

        service.deleteOldSubmissionVersions(dayjs(deleteFrom), dayjs(deleteTo)).subscribe((res) => {
            expect(res.body).toEqual(mockExecutionRecord);
        });

        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `api/core/admin/cleanup/old-submission-versions?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('DELETE');
        req.flush(mockExecutionRecord);
    });

    it('should send GET request to count old submission versions with date range', () => {
        const deleteFrom = '2024-03-07T13:06:36.100Z';
        const deleteTo = '2024-03-08T13:06:36.100Z';

        service.countOldSubmissionVersions(dayjs(deleteFrom), dayjs(deleteTo)).subscribe((res) => {
            expect(res.body).toEqual(mockSubmissionVersionsCount);
        });

        const req = httpMock.expectOne({
            method: 'GET',
            url: `api/core/admin/cleanup/old-submission-versions/count?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('GET');
        req.flush(mockSubmissionVersionsCount);
    });
});
