/**
 * Vitest tests for FileUploadSubmissionService.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { FileUploadSubmissionService } from './file-upload-submission.service';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

describe('FileUploadSubmissionService', () => {
    setupTestBed({ zoneless: true });

    let service: FileUploadSubmissionService;
    let httpMock: HttpTestingController;
    let submissionService: SubmissionService;

    const createSubmission = (id?: number): FileUploadSubmission => {
        const submission = new FileUploadSubmission();
        submission.id = id ?? 1;
        submission.submitted = true;
        submission.submissionDate = dayjs();
        submission.filePath = '/api/files/test.pdf';
        return submission;
    };

    const createParticipation = (id?: number): StudentParticipation => {
        const participation = new StudentParticipation();
        participation.id = id ?? 42;
        return participation;
    };

    const createFile = (name = 'test.pdf', type = 'application/pdf'): File => {
        return new File(['test content'], name, { type });
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                FileUploadSubmissionService,
                {
                    provide: SubmissionService,
                    useValue: {
                        convert: vi.fn((s: FileUploadSubmission) => Object.assign(new FileUploadSubmission(), s)),
                        convertArrayResponse: vi.fn((res) => res),
                        convertSubmissionFromServer: vi.fn((s) => s),
                        convertSubmissionResponseFromServer: vi.fn((res) => res),
                    },
                },
            ],
        });

        service = TestBed.inject(FileUploadSubmissionService);
        httpMock = TestBed.inject(HttpTestingController);
        submissionService = TestBed.inject(SubmissionService);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('update', () => {
        it('should update submission with file', async () => {
            const submission = createSubmission();
            const file = createFile();
            const exerciseId = 123;
            const expectedSubmission = Object.assign({}, submission, { id: 999 });

            const resultPromise = new Promise<HttpResponse<FileUploadSubmission>>((resolve) => {
                service.update(submission, exerciseId, file).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({
                method: 'POST',
                url: `api/fileupload/exercises/${exerciseId}/file-upload-submissions`,
            });

            // Verify FormData is sent
            expect(req.request.body).toBeInstanceOf(FormData);

            req.flush(expectedSubmission);

            const response = await resultPromise;
            expect(response.body).toBeDefined();
        });

        it('should include file in FormData', async () => {
            const submission = createSubmission();
            const file = createFile('document.pdf');
            const exerciseId = 123;

            service.update(submission, exerciseId, file).subscribe();

            const req = httpMock.expectOne({
                method: 'POST',
                url: `api/fileupload/exercises/${exerciseId}/file-upload-submissions`,
            });

            const formData = req.request.body as FormData;
            expect(formData.has('file')).toBe(true);
            expect(formData.has('submission')).toBe(true);

            req.flush({});
        });

        it('should call submission service convert', async () => {
            const submission = createSubmission();
            const file = createFile();

            service.update(submission, 123, file).subscribe();

            const req = httpMock.expectOne({ method: 'POST' });
            req.flush({});

            expect(submissionService.convert).toHaveBeenCalledWith(submission);
        });

        it('should set filePathUrl from response', async () => {
            const submission = createSubmission();
            const file = createFile();
            const responseSubmission = Object.assign({}, submission, { filePath: '/api/files/response.pdf' });

            service.update(submission, 123, file).subscribe();

            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(responseSubmission);

            // Service should process the response
            expect(submissionService.convertSubmissionResponseFromServer).toHaveBeenCalled();
        });
    });

    describe('get', () => {
        it('should get submission by ID', async () => {
            const submission = createSubmission(456);

            const resultPromise = new Promise<HttpResponse<FileUploadSubmission>>((resolve) => {
                service.get(456).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({
                method: 'GET',
                url: 'api/fileupload/file-upload-submissions/456?correction-round=0',
            });

            req.flush(submission);

            const response = await resultPromise;
            expect(response.body?.id).toBe(456);
        });

        it('should pass correction round as parameter', async () => {
            const submission = createSubmission();

            service.get(456, 2).subscribe();

            const req = httpMock.expectOne((r) => {
                return r.url === 'api/fileupload/file-upload-submissions/456' && r.params.get('correction-round') === '2';
            });

            req.flush(submission);
        });

        it('should pass resultId when provided', async () => {
            const submission = createSubmission();

            service.get(456, 0, 789).subscribe();

            const req = httpMock.expectOne((r) => {
                return r.url === 'api/fileupload/file-upload-submissions/456' && r.params.get('resultId') === '789';
            });

            req.flush(submission);
        });

        it('should use resultId instead of correction-round when provided', async () => {
            const submission = createSubmission();

            service.get(456, 1, 789).subscribe();

            const req = httpMock.expectOne((r) => {
                return r.url === 'api/fileupload/file-upload-submissions/456' && r.params.get('resultId') === '789' && !r.params.has('correction-round');
            });

            req.flush(submission);
        });
    });

    describe('getSubmissions', () => {
        it('should get submissions for exercise', async () => {
            const submissions = [createSubmission(1), createSubmission(2)];

            const resultPromise = new Promise<HttpResponse<FileUploadSubmission[]>>((resolve) => {
                service.getSubmissions(123, {}).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({
                method: 'GET',
                url: 'api/fileupload/exercises/123/file-upload-submissions',
            });

            req.flush(submissions);

            const response = await resultPromise;
            expect(response.body?.length).toBe(2);
        });

        it('should pass submittedOnly filter', async () => {
            service.getSubmissions(123, { submittedOnly: true }).subscribe();

            const req = httpMock.expectOne((r) => {
                return r.url === 'api/fileupload/exercises/123/file-upload-submissions' && r.params.get('submittedOnly') === 'true';
            });

            req.flush([]);
        });

        it('should pass assessedByTutor filter', async () => {
            service.getSubmissions(123, { assessedByTutor: true }).subscribe();

            const req = httpMock.expectOne((r) => {
                return r.url === 'api/fileupload/exercises/123/file-upload-submissions' && r.params.get('assessedByTutor') === 'true';
            });

            req.flush([]);
        });

        it('should pass correction round when not zero', async () => {
            service.getSubmissions(123, {}, 2).subscribe();

            const req = httpMock.expectOne((r) => {
                return r.url === 'api/fileupload/exercises/123/file-upload-submissions' && r.params.get('correction-round') === '2';
            });

            req.flush([]);
        });

        it('should not pass correction round when zero', async () => {
            service.getSubmissions(123, {}, 0).subscribe();

            const req = httpMock.expectOne((r) => {
                return r.url === 'api/fileupload/exercises/123/file-upload-submissions' && !r.params.has('correction-round');
            });

            req.flush([]);
        });
    });

    describe('getSubmissionWithoutAssessment', () => {
        it('should get submission without assessment', async () => {
            const submission = createSubmission();

            const resultPromise = new Promise<FileUploadSubmission | undefined>((resolve) => {
                service.getSubmissionWithoutAssessment(123).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({
                method: 'GET',
                url: 'api/fileupload/exercises/123/file-upload-submission-without-assessment',
            });

            req.flush(submission);

            const result = await resultPromise;
            expect(result).toBeDefined();
        });

        it('should return undefined when no submission available', async () => {
            const resultPromise = new Promise<FileUploadSubmission | undefined>((resolve) => {
                service.getSubmissionWithoutAssessment(123).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({
                method: 'GET',
                url: 'api/fileupload/exercises/123/file-upload-submission-without-assessment',
            });

            req.flush(null);

            const result = await resultPromise;
            expect(result).toBeUndefined();
        });

        it('should pass lock parameter', async () => {
            service.getSubmissionWithoutAssessment(123, true).subscribe();

            const req = httpMock.expectOne((r) => {
                return r.url === 'api/fileupload/exercises/123/file-upload-submission-without-assessment' && r.params.get('lock') === 'true';
            });

            req.flush({});
        });

        it('should pass correction round when not zero', async () => {
            service.getSubmissionWithoutAssessment(123, false, 2).subscribe();

            const req = httpMock.expectOne((r) => {
                return r.url === 'api/fileupload/exercises/123/file-upload-submission-without-assessment' && r.params.get('correction-round') === '2';
            });

            req.flush({});
        });
    });

    describe('getDataForFileUploadEditor', () => {
        it('should get data for file upload editor', async () => {
            const submission = createSubmission();
            submission.participation = createParticipation(42);

            const resultPromise = new Promise<FileUploadSubmission>((resolve) => {
                service.getDataForFileUploadEditor(42).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({
                method: 'GET',
                url: 'api/fileupload/participations/42/file-upload-editor',
            });

            req.flush(submission);

            const result = await resultPromise;
            expect(result).toBeDefined();
        });

        it('should call convertSubmissionFromServer', async () => {
            const submission = createSubmission();

            service.getDataForFileUploadEditor(42).subscribe();

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(submission);

            expect(submissionService.convertSubmissionFromServer).toHaveBeenCalled();
        });
    });

    describe('filePathUrl conversion', () => {
        it('should set filePathUrl from filePath on conversion', async () => {
            const submission = createSubmission();
            submission.filePath = '/api/files/submissions/test.pdf';

            const resultPromise = new Promise<HttpResponse<FileUploadSubmission>>((resolve) => {
                service.get(123).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(submission);

            await resultPromise;
            // The conversion should set filePathUrl
            expect(submissionService.convertSubmissionResponseFromServer).toHaveBeenCalled();
        });
    });
});
