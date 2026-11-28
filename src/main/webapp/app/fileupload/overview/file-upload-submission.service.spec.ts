import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
/**
 * Vitest tests for FileUploadSubmissionService.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { MockService } from 'ng-mocks';

import { FileUploadSubmissionService } from './file-upload-submission.service';
import { FileUploadParticipation, FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { FileUploadSubmission as FileUploadSubmissionResponse } from 'app/openapi/model/file-upload-submission';
import { FileUploadExerciseContext } from 'app/openapi/model/file-upload-exercise-context';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { User } from 'app/account/user/user.model';
import { Authority } from 'app/foundation/constants/authority.constants';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exam } from 'app/exam/shared/entities/exam.model';

describe('FileUploadSubmissionService', () => {
    let service: FileUploadSubmissionService;
    let httpMock: HttpTestingController;
    let submissionService: SubmissionService;
    let accountService: AccountService;
    const searchParams = (url: string) => new URL(url, 'http://localhost').searchParams;
    const path = (url: string) => new URL(url, 'http://localhost').pathname;

    /** A submission as the server sends it. */
    const createSubmissionDTO = (id: number, filePath = 'file-upload-exercises/1/submissions/1/test.pdf'): FileUploadSubmissionResponse => ({
        id,
        submitted: true,
        submissionExerciseType: 'file-upload',
        submissionDate: '2026-10-25T10:00:00.000Z',
        filePath,
    });

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
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                { provide: WebsocketService, useValue: MockService(WebsocketService) },
                { provide: FeatureToggleService, useValue: MockService(FeatureToggleService) },
            ],
        });

        service = TestBed.inject(FileUploadSubmissionService);
        httpMock = TestBed.inject(HttpTestingController);
        submissionService = TestBed.inject(SubmissionService);
        accountService = TestBed.inject(AccountService);
        vi.spyOn(submissionService, 'convertSubmissionFromServer');
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('update', () => {
        it('should update submission with file', async () => {
            const submission = createSubmission();
            const file = createFile();
            const exerciseId = 123;

            const resultPromise = new Promise<FileUploadSubmission>((resolve) => {
                service.update(submission, exerciseId, file).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({
                method: 'POST',
                url: `/api/fileupload/exercises/${exerciseId}/file-upload-submissions`,
            });

            // Verify FormData is sent
            expect(req.request.body).toBeInstanceOf(FormData);

            req.flush(createSubmissionDTO(999));

            const response = await resultPromise;
            expect(response).toBeInstanceOf(FileUploadSubmission);
            expect(response.id).toBe(999);
        });

        it('should include file in FormData', async () => {
            const submission = createSubmission();
            const file = createFile('document.pdf');
            const exerciseId = 123;

            service.update(submission, exerciseId, file).subscribe();

            const req = httpMock.expectOne({
                method: 'POST',
                url: `/api/fileupload/exercises/${exerciseId}/file-upload-submissions`,
            });

            const formData = req.request.body as FormData;
            const filePart = formData.get('file');
            expect(filePart).toBeInstanceOf(File);
            expect(filePart instanceof File ? filePart.name : undefined).toBe('document.pdf');
            const submissionPart = formData.get('submission');
            expect(submissionPart).toBeInstanceOf(Blob);
            expect(submissionPart instanceof Blob ? submissionPart.type : undefined).toBe('application/json');

            req.flush(createSubmissionDTO(1));
        });

        it('should serialize only the submission input DTO', async () => {
            const submission = createSubmission();
            submission.filePath = '/api/files/should-not-be-sent.pdf';
            submission.participation = createParticipation();
            const file = createFile();

            service.update(submission, 123, file).subscribe();

            const req = httpMock.expectOne({ method: 'POST' });
            const formData = req.request.body as FormData;
            const submissionBlob = formData.get('submission') as Blob;
            const requestBody = JSON.parse(await submissionBlob.text());
            req.flush(createSubmissionDTO(1));

            expect(requestBody).toEqual({ id: submission.id, submitted: true, exerciseId: 123 });
            expect(requestBody.filePath).toBeUndefined();
            expect(requestBody.participation).toBeUndefined();
        });

        it('should set filePathUrl from response', async () => {
            const submission = createSubmission();
            const file = createFile();
            let response: FileUploadSubmission | undefined;

            service.update(submission, 123, file).subscribe((saved) => (response = saved));

            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(createSubmissionDTO(1, 'file-upload-exercises/1/submissions/1/response.pdf'));

            expect(submissionService.convertSubmissionFromServer).toHaveBeenCalled();
            expect(response?.filePathUrl).toBe('api/core/files/file-upload-exercises/1/submissions/1/response.pdf');
        });
    });

    describe('get', () => {
        it('should get submission by ID', async () => {
            const resultPromise = new Promise<FileUploadSubmission>((resolve) => {
                service.get(456).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({
                method: 'GET',
                url: '/api/fileupload/file-upload-submissions/456?correction-round=0',
            });

            req.flush(createSubmissionDTO(456));

            const response = await resultPromise;
            expect(response.id).toBe(456);
            expect(dayjs.isDayjs(response.submissionDate)).toBe(true);
        });

        it('should pass correction round as parameter', async () => {
            service.get(456, 2).subscribe();

            const req = httpMock.expectOne((r) => {
                return path(r.url) === '/api/fileupload/file-upload-submissions/456' && searchParams(r.url).get('correction-round') === '2' && !searchParams(r.url).has('resultId');
            });

            req.flush(createSubmissionDTO(456));
        });

        it('should pass resultId when provided', async () => {
            service.get(456, 0, 789).subscribe();

            const req = httpMock.expectOne((r) => {
                return path(r.url) === '/api/fileupload/file-upload-submissions/456' && searchParams(r.url).get('resultId') === '789';
            });

            req.flush(createSubmissionDTO(456));
        });

        it('should use resultId instead of correction-round when provided', async () => {
            service.get(456, 1, 789).subscribe();

            const req = httpMock.expectOne((r) => {
                return (
                    path(r.url) === '/api/fileupload/file-upload-submissions/456' && searchParams(r.url).get('resultId') === '789' && !searchParams(r.url).has('correction-round')
                );
            });

            req.flush(createSubmissionDTO(456));
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

        it('should pass assessedByMe filter', async () => {
            service.getSubmissions(123, { assessedByMe: true }).subscribe();

            const req = httpMock.expectOne((r) => {
                return r.url === 'api/fileupload/exercises/123/file-upload-submissions' && r.params.get('assessedByMe') === 'true';
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
            const resultPromise = new Promise<FileUploadSubmission | undefined>((resolve) => {
                service.getSubmissionWithoutAssessment(123).subscribe((resp) => resolve(resp));
            });

            // correction round 0 is the server's default, which the generated client sends explicitly
            const req = httpMock.expectOne({
                method: 'GET',
                url: '/api/fileupload/exercises/123/file-upload-submission-without-assessment?correction-round=0',
            });

            req.flush(createSubmissionDTO(5));

            const result = await resultPromise;
            expect(result).toBeInstanceOf(FileUploadSubmission);
            expect(result?.id).toBe(5);
        });

        it('should return undefined when no submission available', async () => {
            const resultPromise = new Promise<FileUploadSubmission | undefined>((resolve) => {
                service.getSubmissionWithoutAssessment(123).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({
                method: 'GET',
                url: '/api/fileupload/exercises/123/file-upload-submission-without-assessment?correction-round=0',
            });

            // The server answers 200 with an empty body when no submission is left to assess
            req.flush(null);

            const result = await resultPromise;
            expect(result).toBeUndefined();
        });

        it('should pass lock parameter', async () => {
            service.getSubmissionWithoutAssessment(123, true).subscribe();

            const req = httpMock.expectOne((r) => {
                return path(r.url) === '/api/fileupload/exercises/123/file-upload-submission-without-assessment' && searchParams(r.url).get('lock') === 'true';
            });

            req.flush(createSubmissionDTO(5));
        });

        it('should pass correction round when not zero', async () => {
            service.getSubmissionWithoutAssessment(123, false, 2).subscribe();

            const req = httpMock.expectOne((r) => {
                return path(r.url) === '/api/fileupload/exercises/123/file-upload-submission-without-assessment' && searchParams(r.url).get('correction-round') === '2';
            });

            req.flush(createSubmissionDTO(5));
        });
    });

    describe('getDataForFileUploadEditor', () => {
        it('should get data for file upload editor', async () => {
            const resultPromise = new Promise<FileUploadSubmission>((resolve) => {
                service.getDataForFileUploadEditor(42).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({
                method: 'GET',
                url: '/api/fileupload/participations/42/file-upload-editor',
            });

            // The editor of a participation without a submission gets a placeholder without an id
            req.flush({ submissionExerciseType: 'file-upload', participation: { id: 42, type: 'student', testRun: false } });

            const result = await resultPromise;
            expect(result).toBeInstanceOf(FileUploadSubmission);
            expect(result.id).toBeUndefined();
            expect(result.participation).toBeInstanceOf(FileUploadParticipation);
            expect(result.participation?.id).toBe(42);
        });

        it('should preserve non-admin instructor access through the real submission conversion', async () => {
            const courseId = 300;
            accountService.userIdentity.set({
                id: 10,
                authorities: [Authority.STUDENT],
                courseRoles: [{ courseId, roles: ['INSTRUCTOR'] }],
            } as User);
            const exercise: FileUploadExerciseContext = {
                id: 7,
                type: 'file-upload',
                teamMode: false,
                exerciseGroup: {
                    id: 100,
                    exam: {
                        id: 200,
                        course: {
                            id: courseId,
                            maxComplaintTimeDays: 7,
                            maxRequestMoreFeedbackTimeDays: 7,
                            maxComplaintTextLimit: 2000,
                            maxComplaintResponseTextLimit: 2000,
                            complaintsEnabled: true,
                            requestMoreFeedbackEnabled: true,
                        },
                    },
                },
            };
            const dto: FileUploadSubmissionResponse = {
                id: 1,
                submissionExerciseType: 'file-upload',
                participation: {
                    id: 42,
                    type: 'student',
                    testRun: false,
                    exercise,
                },
            };

            const resultPromise = new Promise<FileUploadSubmission>((resolve) => {
                service.getDataForFileUploadEditor(42).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({
                method: 'GET',
                url: '/api/fileupload/participations/42/file-upload-editor',
            });
            req.flush(dto);

            const result = await resultPromise;
            expect(result.participation).toBeInstanceOf(FileUploadParticipation);
            expect(result.participation?.exercise).toBeInstanceOf(FileUploadExercise);
            expect(result.participation?.exercise?.exerciseGroup).toBeInstanceOf(ExerciseGroup);
            expect(result.participation?.exercise?.exerciseGroup?.exam).toBeInstanceOf(Exam);
            expect(result.participation?.exercise?.exerciseGroup?.exam?.course).toBeInstanceOf(Course);
            expect(result.participation?.exercise?.isAtLeastInstructor).toBe(true);
            expect((result.participation as StudentParticipation).student).toBeUndefined();
            expect((result.participation as StudentParticipation).team).toBeUndefined();
        });

        it('should call convertSubmissionFromServer', async () => {
            service.getDataForFileUploadEditor(42).subscribe();

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(createSubmissionDTO(1));

            expect(submissionService.convertSubmissionFromServer).toHaveBeenCalled();
        });
    });

    describe('filePathUrl conversion', () => {
        it('should set filePathUrl from filePath on conversion', async () => {
            const resultPromise = new Promise<FileUploadSubmission>((resolve) => {
                service.get(123).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(createSubmissionDTO(123, 'file-upload-exercises/1/submissions/123/test.pdf'));

            const submission = await resultPromise;
            expect(submission.filePath).toBe('file-upload-exercises/1/submissions/123/test.pdf');
            expect(submission.filePathUrl).toBe('api/core/files/file-upload-exercises/1/submissions/123/test.pdf');
        });
    });

    it.each([0, 2])('loads my assessed submissions through the tutor route for round %s', (correctionRound) => {
        service.getMyAssessedSubmissions(123, correctionRound).subscribe((response) => expect(response.body).toEqual([]));
        const req = httpMock.expectOne((request) => request.url === 'api/fileupload/exercises/123/file-upload-submissions/assessed-by-me');
        expect(req.request.method).toBe('GET');
        expect(req.request.params.get('correction-round')).toBe(correctionRound === 0 ? null : '2');
        expect(req.request.params.has('assessedByTutor')).toBe(false);
        req.flush([]);
    });

    it('keeps the instructor self filter on the instructor route', () => {
        service.getSubmissions(123, { submittedOnly: true, assessedByMe: true }, 2).subscribe();
        const req = httpMock.expectOne((request) => request.url === 'api/fileupload/exercises/123/file-upload-submissions');
        expect(req.request.params.get('submittedOnly')).toBe('true');
        expect(req.request.params.get('assessedByMe')).toBe('true');
        expect(req.request.params.get('correction-round')).toBe('2');
        req.flush([]);
    });
    it.each(['owner', 'staff-preview'] as const)('selects file editor URL from explicit %s purpose', (purpose) => {
        service.getDataForFileUploadEditor(123, purpose).subscribe((data) => expect(data.id).toBe(123));
        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe(`/api/fileupload/participations/123/file-upload-editor${purpose === 'staff-preview' ? '/for-staff' : ''}`);
        req.flush(createSubmissionDTO(123));
    });
    it.each(['owner', 'staff-preview'] as const)('normalizes nested editor results and feedback for %s without changing projection', async (purpose) => {
        const resultPromise = new Promise<FileUploadSubmission>((resolve) => service.getDataForFileUploadEditor(42, purpose).subscribe(resolve));
        const request = httpMock.expectOne({ method: 'GET' });
        request.flush({
            id: 789,
            submitted: true,
            submissionDate: '2026-10-25T10:00:00Z',
            results: [
                {
                    id: 999,
                    completionDate: '2026-10-25T12:00:00Z',
                    rated: true,
                    score: 80,
                    hasComplaint: true,
                    assessor: purpose === 'staff-preview' ? { id: 22, login: 'assessor' } : undefined,
                    feedbacks: [
                        {
                            id: 81,
                            reference: 'element:7',
                            credits: 3,
                            type: FeedbackType.MANUAL,
                            hasLongFeedbackText: true,
                            gradingInstruction: { id: 51, credits: 3, instructionDescription: 'Explain the solution' },
                        },
                    ],
                },
            ],
        });
        const submission = await resultPromise;
        const result = submission.latestResult!;
        expect(dayjs.isDayjs(result.completionDate)).toBe(true);
        expect(result).toBeInstanceOf(Result);
        expect(result.completionDate?.toISOString()).toBe('2026-10-25T12:00:00.000Z');
        expect(dayjs.isDayjs(submission.submissionDate)).toBe(true);
        expect(result.submission).toBe(submission);
        expect(result.feedbacks?.[0]).toBeInstanceOf(Feedback);
        expect(result.feedbacks?.[0].referenceType).toBe('element');
        expect(result.feedbacks?.[0].referenceId).toBe('7');
        expect(result.feedbacks?.[0].hasLongFeedbackText).toBe(true);
        expect(result.feedbacks?.[0].gradingInstruction?.id).toBe(51);
        expect(result.assessor?.login).toBe(purpose === 'staff-preview' ? 'assessor' : undefined);
        expect(result.rated).toBe(true);
        expect(result.score).toBe(80);
        expect(result.hasComplaint).toBe(true);
    });
});
