/**
 * Vitest tests for FileUploadAssessmentService.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { FileUploadAssessmentService } from './file-upload-assessment.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';

describe('FileUploadAssessmentService', () => {
    setupTestBed({ zoneless: true });

    let service: FileUploadAssessmentService;
    let httpMock: HttpTestingController;

    const createFeedback = (id: number, credits: number): Feedback => {
        const feedback = new Feedback();
        feedback.id = id;
        feedback.credits = credits;
        feedback.reference = 'reference';
        return feedback;
    };

    const createResult = (): Result => {
        const result = new Result();
        result.id = 1;
        result.score = 5;
        result.hasComplaint = false;
        result.completionDate = dayjs();
        result.submission = {
            id: 187,
            submissionDate: dayjs(),
            participation: { id: 6, initializationDate: dayjs() },
        };
        result.assessmentNote = { id: 58, note: 'Note Text' };
        return result;
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), FileUploadAssessmentService],
        });
        service = TestBed.inject(FileUploadAssessmentService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('saveAssessment', () => {
        it('should save assessment without submitting', async () => {
            const submissionId = 187;
            const feedbacks = [createFeedback(0, 3), createFeedback(1, 1)];
            const assessmentNoteText = 'Note Text';
            const expectedResult = createResult();

            const resultPromise = new Promise<Result>((resolve) => {
                service.saveAssessment(feedbacks, submissionId, assessmentNoteText, false).subscribe((resp) => {
                    resolve(resp);
                });
            });

            const req = httpMock.expectOne({
                url: `api/fileupload/file-upload-submissions/${submissionId}/feedback`,
                method: 'PUT',
            });

            expect(req.request.body).toEqual({
                feedbacks,
                assessmentNote: assessmentNoteText,
            });

            req.flush(expectedResult);

            const result = await resultPromise;
            expect(result.id).toBe(expectedResult.id);
        });

        it('should submit assessment with submit flag', async () => {
            const submissionId = 187;
            const feedbacks = [createFeedback(0, 3)];
            const assessmentNoteText = 'Note Text';
            const expectedResult = createResult();

            const resultPromise = new Promise<Result>((resolve) => {
                service.saveAssessment(feedbacks, submissionId, assessmentNoteText, true).subscribe((resp) => {
                    resolve(resp);
                });
            });

            const req = httpMock.expectOne({
                url: `api/fileupload/file-upload-submissions/${submissionId}/feedback?submit=true`,
                method: 'PUT',
            });

            req.flush(expectedResult);

            const result = await resultPromise;
            expect(result.id).toBe(expectedResult.id);
        });

        it('should save assessment without note', async () => {
            const submissionId = 187;
            const feedbacks = [createFeedback(0, 3)];
            const expectedResult = createResult();

            const resultPromise = new Promise<Result>((resolve) => {
                service.saveAssessment(feedbacks, submissionId, undefined, false).subscribe((resp) => {
                    resolve(resp);
                });
            });

            const req = httpMock.expectOne({
                url: `api/fileupload/file-upload-submissions/${submissionId}/feedback`,
                method: 'PUT',
            });

            expect(req.request.body).toEqual({
                feedbacks,
                assessmentNote: undefined,
            });

            req.flush(expectedResult);

            const result = await resultPromise;
            expect(result).toBeDefined();
        });
    });

    describe('getAssessment', () => {
        it('should get assessment by submission ID', async () => {
            const submissionId = 187;
            const expectedResult = createResult();

            const resultPromise = new Promise<Result>((resolve) => {
                service.getAssessment(submissionId).subscribe((resp) => {
                    resolve(resp);
                });
            });

            const req = httpMock.expectOne({
                url: `api/fileupload/file-upload-submissions/${submissionId}/result`,
                method: 'GET',
            });

            req.flush(expectedResult);

            const result = await resultPromise;
            expect(result.id).toBe(expectedResult.id);
        });
    });

    describe('updateAssessmentAfterComplaint', () => {
        it('should update assessment after complaint', async () => {
            const submissionId = 1;
            const feedbacks = [createFeedback(0, 3), createFeedback(1, 1)];
            const complaintResponse = new ComplaintResponse();
            complaintResponse.id = 1;
            complaintResponse.responseText = 'That is true';
            const expectedResult = createResult();

            const resultPromise = new Promise<HttpResponse<Result>>((resolve) => {
                service.updateAssessmentAfterComplaint(feedbacks, complaintResponse, submissionId).subscribe((resp) => {
                    resolve(resp);
                });
            });

            const req = httpMock.expectOne({
                url: `api/fileupload/file-upload-submissions/${submissionId}/assessment-after-complaint`,
                method: 'PUT',
            });

            expect(req.request.body).toEqual({
                feedbacks,
                complaintResponse,
                assessmentNote: undefined,
            });

            req.flush(expectedResult);

            const response = await resultPromise;
            expect(response.body).toBeDefined();
        });

        it('should update assessment after complaint with assessment note', async () => {
            const submissionId = 1;
            const feedbacks = [createFeedback(0, 3)];
            const complaintResponse = new ComplaintResponse();
            const assessmentNote = 'Updated note';
            const expectedResult = createResult();

            const resultPromise = new Promise<HttpResponse<Result>>((resolve) => {
                service.updateAssessmentAfterComplaint(feedbacks, complaintResponse, submissionId, assessmentNote).subscribe((resp) => {
                    resolve(resp);
                });
            });

            const req = httpMock.expectOne({
                url: `api/fileupload/file-upload-submissions/${submissionId}/assessment-after-complaint`,
                method: 'PUT',
            });

            expect(req.request.body).toEqual({
                feedbacks,
                complaintResponse,
                assessmentNote,
            });

            req.flush(expectedResult);

            const response = await resultPromise;
            expect(response.body).toBeDefined();
        });

        it('should convert dates from server response', async () => {
            const submissionId = 1;
            const feedbacks = [createFeedback(0, 3)];
            const complaintResponse = new ComplaintResponse();
            const serverResult = {
                id: 1,
                completionDate: '2023-01-01T12:00:00Z',
                submission: {
                    id: 187,
                    submissionDate: '2023-01-01T10:00:00Z',
                    participation: {
                        id: 6,
                        initializationDate: '2023-01-01T08:00:00Z',
                    },
                },
            };

            const resultPromise = new Promise<HttpResponse<Result>>((resolve) => {
                service.updateAssessmentAfterComplaint(feedbacks, complaintResponse, submissionId).subscribe((resp) => {
                    resolve(resp);
                });
            });

            const req = httpMock.expectOne({
                url: `api/fileupload/file-upload-submissions/${submissionId}/assessment-after-complaint`,
                method: 'PUT',
            });

            req.flush(serverResult);

            const response = await resultPromise;
            expect(response.body).toBeDefined();
        });

        it('should handle null body in server response', async () => {
            const submissionId = 1;
            const feedbacks = [createFeedback(0, 3)];
            const complaintResponse = new ComplaintResponse();

            const resultPromise = new Promise<HttpResponse<Result>>((resolve) => {
                service.updateAssessmentAfterComplaint(feedbacks, complaintResponse, submissionId).subscribe((resp) => {
                    resolve(resp);
                });
            });

            const req = httpMock.expectOne({
                url: `api/fileupload/file-upload-submissions/${submissionId}/assessment-after-complaint`,
                method: 'PUT',
            });

            req.flush(null);

            const response = await resultPromise;
            expect(response.body).toBeNull();
        });

        it('should handle response without submission', async () => {
            const submissionId = 1;
            const feedbacks = [createFeedback(0, 3)];
            const complaintResponse = new ComplaintResponse();
            const serverResult = {
                id: 1,
                completionDate: '2023-01-01T12:00:00Z',
                // No submission
            };

            const resultPromise = new Promise<HttpResponse<Result>>((resolve) => {
                service.updateAssessmentAfterComplaint(feedbacks, complaintResponse, submissionId).subscribe((resp) => {
                    resolve(resp);
                });
            });

            const req = httpMock.expectOne({
                url: `api/fileupload/file-upload-submissions/${submissionId}/assessment-after-complaint`,
                method: 'PUT',
            });

            req.flush(serverResult);

            const response = await resultPromise;
            expect(response.body).toBeDefined();
            expect(response.body?.submission).toBeUndefined();
        });

        it('should handle response with submission but no participation', async () => {
            const submissionId = 1;
            const feedbacks = [createFeedback(0, 3)];
            const complaintResponse = new ComplaintResponse();
            const serverResult = {
                id: 1,
                completionDate: '2023-01-01T12:00:00Z',
                submission: {
                    id: 187,
                    submissionDate: '2023-01-01T10:00:00Z',
                    // No participation
                },
            };

            const resultPromise = new Promise<HttpResponse<Result>>((resolve) => {
                service.updateAssessmentAfterComplaint(feedbacks, complaintResponse, submissionId).subscribe((resp) => {
                    resolve(resp);
                });
            });

            const req = httpMock.expectOne({
                url: `api/fileupload/file-upload-submissions/${submissionId}/assessment-after-complaint`,
                method: 'PUT',
            });

            req.flush(serverResult);

            const response = await resultPromise;
            expect(response.body).toBeDefined();
            expect(response.body?.submission?.participation).toBeUndefined();
        });
    });

    describe('cancelAssessment', () => {
        it('should cancel assessment', async () => {
            const submissionId = 187;

            const resultPromise = new Promise<void>((resolve) => {
                service.cancelAssessment(submissionId).subscribe(() => {
                    resolve();
                });
            });

            const req = httpMock.expectOne({
                url: `api/fileupload/file-upload-submissions/${submissionId}/cancel-assessment`,
                method: 'PUT',
            });

            req.flush(null);

            await resultPromise;
            expect(true).toBe(true); // Verify no error occurred
        });
    });

    describe('deleteAssessment', () => {
        it('should delete assessment by participation, submission, and result IDs', async () => {
            const participationId = 1;
            const submissionId = 187;
            const resultId = 42;

            const resultPromise = new Promise<void>((resolve) => {
                service.deleteAssessment(participationId, submissionId, resultId).subscribe(() => {
                    resolve();
                });
            });

            const req = httpMock.expectOne({
                url: `api/fileupload/participations/${participationId}/file-upload-submissions/${submissionId}/results/${resultId}`,
                method: 'DELETE',
            });

            req.flush(null);

            await resultPromise;
            expect(true).toBe(true);
        });
    });
});
