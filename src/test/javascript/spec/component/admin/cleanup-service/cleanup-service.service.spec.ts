import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import dayjs from 'dayjs/esm';
import { CleanupServiceExecutionRecordDTO, DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service.ts';

describe('DataCleanupService', () => {
    let service: DataCleanupService;
    let httpMock: HttpTestingController;

    const mockDate = dayjs();
    const mockExecutionRecord: CleanupServiceExecutionRecordDTO = { executionDate: mockDate, jobType: 'deleteOrphans' };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [DataCleanupService],
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

        const req = httpMock.expectOne({ method: 'DELETE', url: 'api/admin/cleanup/delete-orphans' });
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
            url: `api/admin/cleanup/delete-plagiarism-comparisons?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
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
            url: `api/admin/cleanup/delete-non-rated-results?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('DELETE');
        expect(req.request.params.get('deleteFrom')).toBe(deleteFrom);
        expect(req.request.params.get('deleteTo')).toBe(deleteTo);
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
            url: `api/admin/cleanup/delete-old-rated-results?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('DELETE');
        expect(req.request.params.get('deleteFrom')).toBe(deleteFrom);
        expect(req.request.params.get('deleteTo')).toBe(deleteTo);
        req.flush(mockExecutionRecord);
    });

    it('should send GET request to get last executions', () => {
        const mockExecutionRecords = [mockExecutionRecord];

        service.getLastExecutions().subscribe((res) => {
            expect(res.body).toEqual(mockExecutionRecords);
        });

        const req = httpMock.expectOne({ method: 'GET', url: 'api/admin/cleanup/last-executions' });
        expect(req.request.method).toBe('GET');
        req.flush(mockExecutionRecords);
    });
});
