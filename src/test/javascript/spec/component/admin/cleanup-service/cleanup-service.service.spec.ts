import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import dayjs from 'dayjs/esm';
import { CleanupServiceExecutionRecordDTO, DataCleanupService } from 'app/admin/cleanup-service/cleanup-service.service';

describe('DataCleanupService', () => {
    let service: DataCleanupService;
    let httpMock: HttpTestingController;

    const mockDate = dayjs();
    const mockExecutionRecord: CleanupServiceExecutionRecordDTO = { executionDate: mockDate };

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

    it('should send POST request to delete orphans', () => {
        service.deleteOrphans().subscribe((res) => {
            expect(res.body).toEqual(mockExecutionRecord);
        });

        const req = httpMock.expectOne({ method: 'POST', url: 'api/admin/delete-orphans' });
        expect(req.request.method).toBe('POST');
        req.flush(mockExecutionRecord);
    });

    it('should send POST request to delete plagiarism comparisons with date range', () => {
        const deleteFrom = '2024-03-07T13:06:36.100Z';
        const deleteTo = '2024-03-08T13:06:36.100Z';

        service.deletePlagiarismComparisons(deleteFrom, deleteTo).subscribe((res) => {
            expect(res.body).toEqual(mockExecutionRecord);
        });

        const req = httpMock.expectOne({
            method: 'POST',
            url: `api/admin/delete-plagiarism-comparisons?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('POST');
        expect(req.request.params.get('deleteFrom')).toBe(deleteFrom);
        expect(req.request.params.get('deleteTo')).toBe(deleteTo);
        req.flush(mockExecutionRecord);
    });

    it('should send POST request to delete non-rated results with date range', () => {
        const deleteFrom = '2024-03-07T13:06:36.100Z';
        const deleteTo = '2024-03-08T13:06:36.100Z';

        service.deleteNonRatedResults(deleteFrom, deleteTo).subscribe((res) => {
            expect(res.body).toEqual(mockExecutionRecord);
        });

        const req = httpMock.expectOne({
            method: 'POST',
            url: `api/admin/delete-non-rated-results?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('POST');
        expect(req.request.params.get('deleteFrom')).toBe(deleteFrom);
        expect(req.request.params.get('deleteTo')).toBe(deleteTo);
        req.flush(mockExecutionRecord);
    });

    it('should send POST request to delete old rated results with date range', () => {
        const deleteFrom = '2024-03-07T13:06:36.100Z';
        const deleteTo = '2024-03-08T13:06:36.100Z';

        service.deleteOldRatedResults(deleteFrom, deleteTo).subscribe((res) => {
            expect(res.body).toEqual(mockExecutionRecord);
        });

        const req = httpMock.expectOne({
            method: 'POST',
            url: `api/admin/delete-old-rated-results?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('POST');
        expect(req.request.params.get('deleteFrom')).toBe(deleteFrom);
        expect(req.request.params.get('deleteTo')).toBe(deleteTo);
        req.flush(mockExecutionRecord);
    });

    it('should send POST request to delete old submission versions with date range', () => {
        const deleteFrom = '2024-03-07T13:06:36.100Z';
        const deleteTo = '2024-03-08T13:06:36.100Z';

        service.deleteOldSubmissionVersions(deleteFrom, deleteTo).subscribe((res) => {
            expect(res.body).toEqual(mockExecutionRecord);
        });

        const req = httpMock.expectOne({
            method: 'POST',
            url: `api/admin/delete-old-submission-versions?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('POST');
        expect(req.request.params.get('deleteFrom')).toBe(deleteFrom);
        expect(req.request.params.get('deleteTo')).toBe(deleteTo);
        req.flush(mockExecutionRecord);
    });

    it('should send POST request to delete old feedback with date range', () => {
        const deleteFrom = '2024-03-07T13:06:36.100Z';
        const deleteTo = '2024-03-08T13:06:36.100Z';

        service.deleteOldFeedback(deleteFrom, deleteTo).subscribe((res) => {
            expect(res.body).toEqual(mockExecutionRecord);
        });

        const req = httpMock.expectOne({
            method: 'POST',
            url: `api/admin/delete-old-feedback?deleteFrom=${deleteFrom}&deleteTo=${deleteTo}`,
        });

        expect(req.request.method).toBe('POST');
        expect(req.request.params.get('deleteFrom')).toBe(deleteFrom);
        expect(req.request.params.get('deleteTo')).toBe(deleteTo);
        req.flush(mockExecutionRecord);
    });

    it('should send POST request to get last executions', () => {
        const mockExecutionRecords = [mockExecutionRecord];

        service.getLastExecutions().subscribe((res) => {
            expect(res.body).toEqual(mockExecutionRecords);
        });

        const req = httpMock.expectOne({ method: 'POST', url: 'api/admin/get-last-executions' });
        expect(req.request.method).toBe('POST');
        req.flush(mockExecutionRecords);
    });
});
