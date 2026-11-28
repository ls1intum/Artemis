/**
 * Vitest tests for FileUploadAssessmentService, through the generated FileUploadAssessmentApi.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';

import { FileUploadAssessmentService } from './file-upload-assessment.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { FileUploadResult } from 'app/openapi/model/file-upload-result';
import { FileUploadFeedbackInput } from 'app/openapi/model/file-upload-feedback-input';

describe('FileUploadAssessmentService', () => {
    let service: FileUploadAssessmentService;
    let httpMock: HttpTestingController;

    const createFeedback = (id: number, credits: number): Feedback => {
        const feedback = new Feedback();
        feedback.id = id;
        feedback.credits = credits;
        feedback.reference = 'reference';
        feedback.type = FeedbackType.MANUAL;
        return feedback;
    };

    const createResultDTO = (): FileUploadResult => ({
        id: 1,
        score: 5,
        rated: false,
        hasComplaint: false,
        completionDate: '2023-01-01T12:00:00.000Z',
        submission: {
            id: 187,
            submissionExerciseType: 'file-upload',
            submissionDate: '2023-01-01T10:00:00.000Z',
            filePath: 'file-upload-exercises/1/submissions/187/answer.pdf',
            participation: { id: 6, type: 'student', testRun: false, initializationDate: '2023-01-01T08:00:00.000Z' },
        },
        assessmentNote: { id: 58, note: 'Note Text' },
        feedbacks: [{ id: 7, detailText: 'Feedback text', credits: 1, type: 'MANUAL_UNREFERENCED', visibility: 'ALWAYS' }],
    });

    const createComplaintResponse = (): ComplaintResponse => {
        const complaintResponse = new ComplaintResponse();
        complaintResponse.id = 1;
        complaintResponse.responseText = 'That is true';
        complaintResponse.complaint = new Complaint();
        complaintResponse.complaint.accepted = true;
        return complaintResponse;
    };

    const expectedFeedbackInput = (feedback: Feedback): FileUploadFeedbackInput => ({
        id: feedback.id,
        text: feedback.text,
        detailText: feedback.detailText,
        reference: feedback.reference,
        credits: feedback.credits,
        positive: feedback.positive,
        type: feedback.type,
        visibility: feedback.testCase?.visibility,
        gradingInstruction: undefined,
    });

    const feedbackUrl = (submissionId: number) => `/api/fileupload/file-upload-submissions/${submissionId}/feedback`;
    const complaintUrl = (submissionId: number) => `/api/fileupload/file-upload-submissions/${submissionId}/assessment-after-complaint`;

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
            const expectedResult = createResultDTO();

            const resultPromise = new Promise<Result>((resolve) => {
                service.saveAssessment(feedbacks, submissionId, assessmentNoteText, false).subscribe((resp) => resolve(resp));
            });

            // submit=false is the server's default, which the generated client sends explicitly
            const req = httpMock.expectOne({ url: `${feedbackUrl(submissionId)}?submit=false`, method: 'PUT' });
            expect(req.request.body).toEqual({
                feedbacks: feedbacks.map(expectedFeedbackInput),
                assessmentNote: assessmentNoteText,
            });

            req.flush(expectedResult);

            const result = await resultPromise;
            expect(result.id).toBe(expectedResult.id);
            expect(result).toBeInstanceOf(Result);
            expect(result.feedbacks?.[0]).toBeInstanceOf(Feedback);
            expect(result.feedbacks?.[0].type).toBe(FeedbackType.MANUAL_UNREFERENCED);
            expect(result.feedbacks?.[0].detailText).toBe('Feedback text');
        });

        it('should submit assessment with submit flag', async () => {
            const submissionId = 187;
            const expectedResult = createResultDTO();

            const resultPromise = new Promise<Result>((resolve) => {
                service.saveAssessment([createFeedback(0, 3)], submissionId, 'Note Text', true).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({ url: `${feedbackUrl(submissionId)}?submit=true`, method: 'PUT' });
            req.flush(expectedResult);

            const result = await resultPromise;
            expect(result.id).toBe(expectedResult.id);
        });

        it('should save assessment without note', async () => {
            const submissionId = 187;
            const feedbacks = [createFeedback(0, 3)];

            const resultPromise = new Promise<Result>((resolve) => {
                service.saveAssessment(feedbacks, submissionId, undefined).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({ url: `${feedbackUrl(submissionId)}?submit=false`, method: 'PUT' });
            expect(req.request.body).toEqual({
                feedbacks: feedbacks.map(expectedFeedbackInput),
                assessmentNote: undefined,
            });

            req.flush(createResultDTO());

            const result = await resultPromise;
            expect(result).toBeDefined();
        });
    });

    describe('updateAssessmentAfterComplaint', () => {
        it('should update assessment after complaint', async () => {
            const submissionId = 1;
            const feedbacks = [createFeedback(0, 3), createFeedback(1, 1)];
            const complaintResponse = createComplaintResponse();

            const resultPromise = new Promise<Result>((resolve) => {
                service.updateAssessmentAfterComplaint(feedbacks, complaintResponse, submissionId).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({ url: complaintUrl(submissionId), method: 'PUT' });
            expect(req.request.body).toEqual({
                feedbacks: feedbacks.map(expectedFeedbackInput),
                complaintResponse: {
                    id: complaintResponse.id,
                    responseText: complaintResponse.responseText,
                    complaintIsAccepted: complaintResponse.complaint?.accepted,
                },
                assessmentNote: undefined,
            });

            req.flush(createResultDTO());

            const result = await resultPromise;
            expect(result).toBeInstanceOf(Result);
            expect(result.id).toBe(1);
        });

        it('should update assessment after complaint with assessment note', async () => {
            const submissionId = 1;
            const feedbacks = [createFeedback(0, 3)];
            const complaintResponse = createComplaintResponse();
            const assessmentNote = 'Updated note';

            const resultPromise = new Promise<Result>((resolve) => {
                service.updateAssessmentAfterComplaint(feedbacks, complaintResponse, submissionId, assessmentNote).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({ url: complaintUrl(submissionId), method: 'PUT' });
            expect(req.request.body.assessmentNote).toBe(assessmentNote);

            req.flush(createResultDTO());

            const result = await resultPromise;
            expect(result).toBeDefined();
        });

        it('should reject a complaint update without a response ID', () => {
            const complaintResponse = createComplaintResponse();
            complaintResponse.id = undefined;

            expect(() => service.updateAssessmentAfterComplaint([], complaintResponse, 1)).toThrow(
                'Cannot update an assessment without a complaint response ID and acceptance decision.',
            );
        });

        it('should reject a complaint update without an acceptance decision', () => {
            const complaintResponse = createComplaintResponse();
            complaintResponse.complaint = new Complaint();

            expect(() => service.updateAssessmentAfterComplaint([], complaintResponse, 1)).toThrow(
                'Cannot update an assessment without a complaint response ID and acceptance decision.',
            );
        });

        it('should convert dates and the submission from the server response', async () => {
            const submissionId = 1;

            const resultPromise = new Promise<Result>((resolve) => {
                service.updateAssessmentAfterComplaint([createFeedback(0, 3)], createComplaintResponse(), submissionId).subscribe((resp) => resolve(resp));
            });

            httpMock.expectOne({ url: complaintUrl(submissionId), method: 'PUT' }).flush(createResultDTO());

            const result = await resultPromise;
            expect(result.completionDate?.isSame(dayjs('2023-01-01T12:00:00Z'))).toBe(true);
            expect(result.submission).toBeInstanceOf(FileUploadSubmission);
            expect(result.submission?.submissionDate?.isSame(dayjs('2023-01-01T10:00:00Z'))).toBe(true);
            expect(result.submission?.participation?.initializationDate?.isSame(dayjs('2023-01-01T08:00:00Z'))).toBe(true);
            expect(result.submission instanceof FileUploadSubmission ? result.submission.filePathUrl : undefined).toBe(
                'api/core/files/file-upload-exercises/1/submissions/187/answer.pdf',
            );
        });

        it('should handle response without submission', async () => {
            const submissionId = 1;

            const resultPromise = new Promise<Result>((resolve) => {
                service.updateAssessmentAfterComplaint([createFeedback(0, 3)], createComplaintResponse(), submissionId).subscribe((resp) => resolve(resp));
            });

            httpMock.expectOne({ url: complaintUrl(submissionId), method: 'PUT' }).flush({ id: 1, rated: false, completionDate: '2023-01-01T12:00:00Z' });

            const result = await resultPromise;
            expect(result).toBeDefined();
            expect(result.submission).toBeUndefined();
        });

        it('should handle response with submission but no participation', async () => {
            const submissionId = 1;
            const serverResult: FileUploadResult = {
                id: 1,
                rated: false,
                completionDate: '2023-01-01T12:00:00Z',
                submission: { id: 187, submissionExerciseType: 'file-upload', submissionDate: '2023-01-01T10:00:00Z' },
            };

            const resultPromise = new Promise<Result>((resolve) => {
                service.updateAssessmentAfterComplaint([createFeedback(0, 3)], createComplaintResponse(), submissionId).subscribe((resp) => resolve(resp));
            });

            httpMock.expectOne({ url: complaintUrl(submissionId), method: 'PUT' }).flush(serverResult);

            const result = await resultPromise;
            expect(result.submission?.participation).toBeUndefined();
        });
    });

    describe('cancelAssessment', () => {
        it('should cancel the newest round without a result id and without a body', async () => {
            const submissionId = 187;

            const resultPromise = new Promise<void>((resolve) => {
                service.cancelAssessment(submissionId).subscribe({ complete: () => resolve() });
            });

            const req = httpMock.expectOne({ url: `/api/fileupload/file-upload-submissions/${submissionId}/cancel-assessment`, method: 'PUT' });
            expect(req.request.body).toBeNull();
            req.flush(null);

            await resultPromise;
        });

        it('should cancel the round of the given result', async () => {
            const resultPromise = new Promise<void>((resolve) => {
                service.cancelAssessment(187, 42).subscribe({ complete: () => resolve() });
            });

            httpMock.expectOne({ url: '/api/fileupload/file-upload-submissions/187/cancel-assessment?resultId=42', method: 'PUT' }).flush(null);

            await resultPromise;
        });
    });

    describe('deleteAssessment', () => {
        it('should delete assessment by participation, submission, and result IDs', async () => {
            const participationId = 1;
            const submissionId = 187;
            const resultId = 42;

            const resultPromise = new Promise<void>((resolve) => {
                service.deleteAssessment(participationId, submissionId, resultId).subscribe({ complete: () => resolve() });
            });

            httpMock
                .expectOne({ url: `/api/fileupload/participations/${participationId}/file-upload-submissions/${submissionId}/results/${resultId}`, method: 'DELETE' })
                .flush(null);

            await resultPromise;
        });
    });
});
