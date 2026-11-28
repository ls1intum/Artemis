import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ComplaintListQuery, ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import dayjs from 'dayjs/esm';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { Visibility } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import type { Complaint as ComplaintDTO } from 'app/openapi/model/complaint';
import type { ComplaintsForTutorQuery } from 'app/openapi/model/complaints-for-tutor-query';

describe('ComplaintService', () => {
    let complaintService: ComplaintService;
    let accountService: AccountService;
    let httpMock: HttpTestingController;

    const dayjsTime1 = dayjs().utc().year(2022).month(3).date(14).hour(10).minute(35).second(12).millisecond(332);
    const stringTime1 = '2022-04-14T10:35:12.332Z';
    const dayjsTime3 = dayjs();

    const serverComplaint1: ComplaintDTO = { id: 42, complaintType: 'MORE_FEEDBACK', result: {}, submittedTime: stringTime1, complaintText: 'Test text' };
    const serverComplaint2: ComplaintDTO = { id: 43, complaintType: 'COMPLAINT', result: {}, submittedTime: '2022-05-12T18:12:11.140Z', complaintText: 'Test text' };

    let exercise: Exercise;
    let emptyResult: Result;
    const course: Course = {
        maxComplaintTimeDays: 7,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        })
            .compileComponents()
            .then(() => {
                complaintService = TestBed.inject(ComplaintService);
                accountService = TestBed.inject(AccountService);
                httpMock = TestBed.inject(HttpTestingController);
            });

        exercise = {
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };
        emptyResult = new Result();
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('isComplaintLockedForLoggedInUser', () => {
        it('should be false if no visible lock is present', () => {
            const result = complaintService.isComplaintLockedForLoggedInUser(new Complaint(), new TextExercise(undefined, undefined));
            expect(result).toBe(false);
        });

        it('should be false if user has the lock', () => {
            const login = 'testUser';
            const _complaint = new Complaint();
            _complaint.complaintResponse = new ComplaintResponse();
            _complaint.complaintResponse.reviewer = { login } as User;
            _complaint.complaintResponse.isCurrentlyLocked = true;

            const result = complaintService.isComplaintLockedForLoggedInUser(_complaint, new TextExercise(undefined, undefined));

            expect(result).toBe(false);
        });

        it('should be true if user has not the lock', () => {
            const login = 'testUser';
            const anotherLogin = 'anotherTestUser';
            const _complaint = new Complaint();
            _complaint.complaintResponse = new ComplaintResponse();
            _complaint.complaintResponse.reviewer = { login } as User;
            _complaint.complaintResponse.isCurrentlyLocked = true;
            accountService.userIdentity.set({ login: anotherLogin } as User);
            vi.spyOn(accountService, 'isAtLeastInstructorForExercise').mockReturnValue(false);

            const result = complaintService.isComplaintLockedForLoggedInUser(_complaint, new TextExercise(undefined, undefined));

            expect(result).toBe(true);
        });

        it('should be false if user is instructor', () => {
            const login = 'testUser';
            const anotherLogin = 'anotherTestUser';
            const _complaint = new Complaint();
            _complaint.complaintResponse = new ComplaintResponse();
            _complaint.complaintResponse.reviewer = { login } as User;
            _complaint.complaintResponse.isCurrentlyLocked = true;
            accountService.userIdentity.set({ login: anotherLogin } as User);
            vi.spyOn(accountService, 'isAtLeastInstructorForExercise').mockReturnValue(true);

            const result = complaintService.isComplaintLockedForLoggedInUser(_complaint, new TextExercise(undefined, undefined));

            expect(result).toBe(false);
        });
    });

    describe('isComplaintLockedByLoggedInUser', () => {
        it('should be false if no visible lock is present', () => {
            const result = complaintService.isComplaintLockedByLoggedInUser(new Complaint());
            expect(result).toBe(false);
        });

        it('should be false if the complaint is not locked', () => {
            const _complaint = new Complaint();
            _complaint.complaintResponse = new ComplaintResponse();

            const result = complaintService.isComplaintLockedByLoggedInUser(_complaint);
            expect(result).toBe(false);
        });

        it('should be false if the complaint has been handled', () => {
            const _complaint = new Complaint();
            _complaint.accepted = true;
            _complaint.complaintResponse = new ComplaintResponse();
            _complaint.complaintResponse.isCurrentlyLocked = true;
            _complaint.complaintResponse.submittedTime = dayjs();

            const result = complaintService.isComplaintLockedByLoggedInUser(_complaint);
            expect(result).toBe(false);
        });

        it('should be false if another user has the lock', () => {
            const login = 'testUser';
            const anotherLogin = 'anotherTestUser';
            const _complaint = new Complaint();
            _complaint.complaintResponse = new ComplaintResponse();
            _complaint.complaintResponse.isCurrentlyLocked = true;
            _complaint.complaintResponse.reviewer = { login } as User;
            accountService.userIdentity.set({ login: anotherLogin } as User);

            const result = complaintService.isComplaintLockedByLoggedInUser(_complaint);
            expect(result).toBe(false);
        });

        it('should be true if the same user has the lock', () => {
            const login = 'testUser';
            const _complaint = new Complaint();
            _complaint.complaintResponse = new ComplaintResponse();
            _complaint.complaintResponse.isCurrentlyLocked = true;
            _complaint.complaintResponse.reviewer = { login } as User;
            accountService.userIdentity.set({ login: login } as User);

            const result = complaintService.isComplaintLockedByLoggedInUser(_complaint);
            expect(result).toBe(true);
        });
    });

    describe('isComplaintLocked', () => {
        it('should be false if no visible lock is present', () => {
            const result = complaintService.isComplaintLocked(new Complaint());
            expect(result).toBe(false);
        });

        it('should be true if locked', () => {
            const _complaint = new Complaint();
            _complaint.complaintResponse = new ComplaintResponse();
            _complaint.complaintResponse.isCurrentlyLocked = true;

            const result = complaintService.isComplaintLocked(_complaint);
            expect(result).toBe(true);
        });
    });

    describe('shouldHighlightComplaint', () => {
        it('should not highlight handled complaints', () => {
            const complaint = {
                id: 42,
                submittedTime: dayjs().subtract(1, 'hours'),
                accepted: true,
            } as Complaint;

            const result = complaintService.shouldHighlightComplaint(complaint);

            expect(result).toBe(false);
        });

        it('should not highlight recent complaints', () => {
            const complaint = {
                id: 42,
                submittedTime: dayjs().subtract(8, 'days').add(1, 'seconds'),
            } as Complaint;

            const result = complaintService.shouldHighlightComplaint(complaint);

            expect(result).toBe(false);
        });

        it('should highlight old complaints', () => {
            const complaint = {
                id: 42,
                submittedTime: dayjs().subtract(8, 'days'),
            } as Complaint;

            const result = complaintService.shouldHighlightComplaint(complaint);

            expect(result).toBe(true);
        });
    });

    describe('getIndividualComplaintDueDate', () => {
        it('should return undefined for no results', () => {
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, undefined);
            expect(individualComplaintDueDate).toBeUndefined();
        });

        it('should return undefined for quiz exercise', () => {
            emptyResult.rated = true;
            emptyResult.completionDate = dayjsTime3;
            exercise.dueDate = undefined;
            exercise.type = ExerciseType.QUIZ;
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toBeUndefined();
        });

        it('should calculate the correct complaint due date for no exercise due date', () => {
            emptyResult.rated = true;
            emptyResult.completionDate = dayjsTime3;
            exercise.dueDate = undefined;
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toEqual(dayjsTime3.add(7, 'days'));
        });

        it('should return undefined for automatic assessment without complaints', () => {
            emptyResult.rated = true;
            exercise.dueDate = dayjsTime3;
            exercise.assessmentType = AssessmentType.AUTOMATIC;
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toBeUndefined();
        });

        it('should calculate the correct complaint due date for automatic assessment with complaints', () => {
            emptyResult.completionDate = dayjsTime3.subtract(1, 'day');
            exercise.allowComplaintsForAutomaticAssessments = true;
            exercise.assessmentType = AssessmentType.AUTOMATIC;
            exercise.dueDate = dayjsTime3;

            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toEqual(dayjsTime3.add(7, 'days'));
        });

        it('should calculate the correct complaint due date for automatic assessment with complaints before dueDate', () => {
            emptyResult.completionDate = dayjsTime3.subtract(2, 'day');
            exercise.allowComplaintsForAutomaticAssessments = true;
            exercise.assessmentType = AssessmentType.AUTOMATIC;
            exercise.dueDate = dayjsTime3.subtract(1, 'day');
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toEqual(dayjsTime3.add(6, 'days'));
        });

        it('should calculate the correct complaint due date after assessmentDueDate', () => {
            emptyResult.rated = true;
            emptyResult.completionDate = dayjsTime3.subtract(3, 'days');
            exercise.assessmentType = AssessmentType.MANUAL;
            exercise.dueDate = dayjsTime3.subtract(2, 'days');
            exercise.assessmentDueDate = dayjsTime3.subtract(1, 'days');
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toEqual(dayjsTime3.add(6, 'days'));
        });

        it('should calculate the correct complaint due date after assessmentDueDate for late feedback', () => {
            emptyResult.rated = true;
            emptyResult.completionDate = dayjsTime3;
            exercise.assessmentType = AssessmentType.MANUAL;
            exercise.dueDate = dayjsTime3.subtract(2, 'days');
            exercise.assessmentDueDate = dayjsTime3.subtract(1, 'days');
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toEqual(dayjsTime3.add(7, 'days'));
        });

        it('should return undefined for complaint due date before assessment dueDate', () => {
            exercise.assessmentType = AssessmentType.MANUAL;
            exercise.assessmentDueDate = dayjsTime3.add(1, 'days');
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toBeUndefined();
        });

        it('should return undefined for complaint due date for unrated result after assessment dueDate', () => {
            emptyResult.rated = false;
            exercise.assessmentType = AssessmentType.MANUAL;
            exercise.assessmentDueDate = dayjsTime3.subtract(1, 'days');
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toBeUndefined();
        });
    });

    /** Expects one GET request and returns it with its path and query parameters, independent of the order the parameters were appended in. */
    function expectGet(): { request: TestRequest; path: string; query: Record<string, string> } {
        const request = httpMock.expectOne(() => true);
        expect(request.request.method).toBe('GET');
        const url = new URL(request.request.urlWithParams, 'http://localhost');
        return { request, path: url.pathname, query: Object.fromEntries(url.searchParams) };
    }

    describe('findAll', () => {
        it.each<[string, ComplaintListQuery, Record<string, string>]>([
            ['the test run of an exercise', { exerciseId: 7 }, { exerciseId: '7' }],
            ['a course', { courseId: 3, complaintType: 'COMPLAINT' }, { courseId: '3', complaintType: 'COMPLAINT' }],
            ['a course and assessor', { courseId: 3, complaintType: 'COMPLAINT', tutorId: 9 }, { courseId: '3', complaintType: 'COMPLAINT', tutorId: '9' }],
            ['an exercise', { exerciseId: 7, complaintType: 'MORE_FEEDBACK' }, { exerciseId: '7', complaintType: 'MORE_FEEDBACK' }],
            ['an exercise and assessor', { exerciseId: 7, complaintType: 'COMPLAINT', tutorId: 9 }, { exerciseId: '7', complaintType: 'COMPLAINT', tutorId: '9' }],
            ['an exam', { courseId: 3, examId: 4 }, { courseId: '3', examId: '4' }],
        ])('requests the complaints of %s', (_scope, query, expectedQuery) => {
            let received: Complaint[] | undefined;
            complaintService.findAll(query).subscribe((complaints) => (received = complaints));

            const { request, path, query: sentQuery } = expectGet();
            expect(path).toBe('/api/assessment/complaints');
            expect(sentQuery).toEqual(expectedQuery);
            request.flush([serverComplaint1, serverComplaint2]);

            expect(received?.map((complaint) => complaint.id)).toEqual([42, 43]);
            expect(received?.[0]).toBeInstanceOf(Complaint);
            expect(received?.[0].complaintType).toBe(ComplaintType.MORE_FEEDBACK);
            expect(received?.[0].submittedTime?.toISOString()).toBe(dayjsTime1.toISOString());
        });

        it('passes an error response on', () => {
            let error: HttpErrorResponse | undefined;
            complaintService.findAll({ courseId: 3, complaintType: 'COMPLAINT' }).subscribe({ error: (received) => (error = received) });

            expectGet().request.flush(null, { status: 403, statusText: 'Forbidden' });

            expect(error?.status).toBe(403);
        });
    });

    describe('findAllForTutor', () => {
        it.each<[string, ComplaintsForTutorQuery, Record<string, string>]>([
            ['the own complaints of a course', { courseId: 3, complaintType: 'COMPLAINT' }, { courseId: '3', complaintType: 'COMPLAINT' }],
            ['the own complaints of an exercise', { exerciseId: 7, complaintType: 'COMPLAINT' }, { exerciseId: '7', complaintType: 'COMPLAINT' }],
            [
                'all complaints of a course',
                { courseId: 3, complaintType: 'COMPLAINT', allComplaintsForTutor: true },
                { courseId: '3', complaintType: 'COMPLAINT', allComplaintsForTutor: 'true' },
            ],
            [
                'all complaints of an exercise',
                { exerciseId: 7, complaintType: 'MORE_FEEDBACK', allComplaintsForTutor: true },
                { exerciseId: '7', complaintType: 'MORE_FEEDBACK', allComplaintsForTutor: 'true' },
            ],
        ])('requests %s without student information', (_scope, query, expectedQuery) => {
            let received: Complaint[] | undefined;
            complaintService.findAllForTutor(query).subscribe((complaints) => (received = complaints));

            const { request, path, query: sentQuery } = expectGet();
            expect(path).toBe('/api/assessment/complaints/for-tutor');
            expect(sentQuery).toEqual(expectedQuery);
            request.flush([serverComplaint2]);

            expect(received?.map((complaint) => complaint.id)).toEqual([43]);
        });
    });

    describe('findBySubmissionId', () => {
        it('requests the complaint of the submission for its owner', () => {
            let received: ComplaintDTO | undefined;
            complaintService.findBySubmissionId(5).subscribe((complaint) => (received = complaint));

            const { request, path, query } = expectGet();
            expect(path).toBe('/api/assessment/complaints');
            expect(query).toEqual({ submissionId: '5' });
            request.flush(serverComplaint1);

            expect(received).toEqual(serverComplaint1);
        });

        it('emits undefined when the server answers with an empty body', () => {
            const emitted: (ComplaintDTO | undefined)[] = [];
            complaintService.findBySubmissionId(5).subscribe((complaint) => emitted.push(complaint));

            expectGet().request.flush(null);

            expect(emitted).toEqual([undefined]);
        });

        it('emits undefined for a list body, which the submission query never returns', () => {
            const emitted: (ComplaintDTO | undefined)[] = [];
            complaintService.findBySubmissionId(5).subscribe((complaint) => emitted.push(complaint));

            expectGet().request.flush([serverComplaint1]);

            expect(emitted).toEqual([undefined]);
        });
    });

    describe('findBySubmissionIdForAssessment', () => {
        it('requests the complaint of the submission for its assessors', () => {
            let received: ComplaintDTO | undefined;
            complaintService.findBySubmissionIdForAssessment(42).subscribe((complaint) => (received = complaint));

            const { request, path, query } = expectGet();
            expect(path).toBe('/api/assessment/complaints/for-assessment');
            expect(query).toEqual({ submissionId: '42' });
            request.flush(serverComplaint2);

            expect(received).toEqual(serverComplaint2);
        });

        it('emits undefined when the server answers with an empty body', () => {
            const emitted: (ComplaintDTO | undefined)[] = [];
            complaintService.findBySubmissionIdForAssessment(42).subscribe((complaint) => emitted.push(complaint));

            expectGet().request.flush(null);

            expect(emitted).toEqual([undefined]);
        });
    });

    it('should remove result references from feedbacks for update after complaint', () => {
        const result = new Result();
        result.id = 1;

        const feedback = new Feedback();
        feedback.id = 10;
        feedback.result = result;

        const sanitizedFeedbacks = complaintService.getFeedbacksForUpdateAfterComplaint([feedback]);

        expect(sanitizedFeedbacks).toHaveLength(1);
        expect(sanitizedFeedbacks[0]).not.toBe(feedback);
        expect(sanitizedFeedbacks[0].id).toBe(feedback.id);
        expect(sanitizedFeedbacks[0].result).toBeUndefined();
        expect(feedback.result).toBe(result);
    });

    it('should sanitize complaint response for update after complaint', () => {
        const complaint = new Complaint();
        complaint.id = 42;
        complaint.accepted = false;
        complaint.complaintType = ComplaintType.COMPLAINT;
        complaint.result = new Result();

        const complaintResponse = new ComplaintResponse();
        complaintResponse.id = 11;
        complaintResponse.responseText = 'response';
        complaintResponse.complaint = complaint;

        const sanitizedComplaintResponse = complaintService.getComplaintResponseForUpdateAfterComplaint(complaintResponse);

        expect(sanitizedComplaintResponse).not.toBe(complaintResponse);
        expect(sanitizedComplaintResponse.id).toBe(11);
        expect(sanitizedComplaintResponse.responseText).toBe('response');
        expect(sanitizedComplaintResponse.complaint).toEqual({
            id: 42,
            accepted: false,
            complaintType: ComplaintType.COMPLAINT,
        });
        expect(sanitizedComplaintResponse.complaint!.result).toBeUndefined();
    });

    describe('convertComplaintFromServer', () => {
        it('should map base fields and attach the provided result', () => {
            const dto: ComplaintDTO = { id: 7, complaintText: 'Why only 80%?', complaintType: 'COMPLAINT', complaintIsAccepted: true, submittedTime: stringTime1 };

            const result = new Result();
            result.id = 99;

            const complaint = complaintService.convertComplaintFromServer(dto, result);

            expect(complaint).toBeInstanceOf(Complaint);
            expect(complaint.id).toBe(7);
            expect(complaint.complaintText).toBe('Why only 80%?');
            expect(complaint.complaintType).toBe(ComplaintType.COMPLAINT);
            expect(complaint.accepted).toBe(true);
            expect(complaint.submittedTime?.toISOString()).toBe(dayjsTime1.toISOString());
            expect(complaint.result).toBe(result);
        });

        it('should tolerate an undefined result', () => {
            const dto: ComplaintDTO = { id: 8 };

            const complaint = complaintService.convertComplaintFromServer(dto, undefined);

            expect(complaint.id).toBe(8);
            expect(complaint.result).toBeUndefined();
        });

        it('should map a student participant onto complaint.student', () => {
            const dto: ComplaintDTO = { participant: { id: 3, name: 'Student One', login: 'student1', isStudent: true } };

            const complaint = complaintService.convertComplaintFromServer(dto, undefined);

            expect(complaint.student).toBeInstanceOf(User);
            expect(complaint.student?.id).toBe(3);
            expect(complaint.student?.name).toBe('Student One');
            expect(complaint.student?.login).toBe('student1');
            expect(complaint.team).toBeUndefined();
        });

        it('should map a team participant onto complaint.team', () => {
            const dto: ComplaintDTO = { participant: { id: 4, name: 'Team Awesome', login: 'team-awesome', isStudent: false } };

            const complaint = complaintService.convertComplaintFromServer(dto, undefined);

            expect(complaint.team).toBeInstanceOf(Team);
            expect(complaint.team?.id).toBe(4);
            expect(complaint.team?.name).toBe('Team Awesome');
            expect(complaint.team?.shortName).toBe('team-awesome');
            expect(complaint.student).toBeUndefined();
        });
    });

    describe('convertComplaintFromServerInList', () => {
        it('should reconstruct the reduced result, exercise title, assessor and feedbacks', () => {
            const dto: ComplaintDTO = {
                id: 11,
                complaintType: 'COMPLAINT',
                complaintIsAccepted: false,
                assessorKey: '9',
                assessorLabel: 'Tutor One',
                result: {
                    id: 55,
                    completionDate: stringTime1,
                    score: 80,
                    rated: true,
                    assessmentType: 'MANUAL',
                    assessor: { id: 9, login: 'tutor1', name: 'Tutor One' },
                    exerciseTitle: 'My Exercise',
                    feedbacks: [{ id: 1, credits: 5, reference: 'reference-id', detailText: 'Good' }],
                    submission: { id: 21, participation: { id: 31, exercise: { id: 41, type: 'text' } } },
                },
            };

            const complaint = complaintService.convertComplaintFromServerInList(dto);

            expect(complaint.id).toBe(11);
            expect(complaint.accepted).toBe(false);
            expect(complaint.assessorKey).toBe('9');
            expect(complaint.assessorLabel).toBe('Tutor One');
            expect(complaint.result).toBeInstanceOf(Result);
            expect(complaint.result?.id).toBe(55);
            expect(complaint.result?.score).toBe(80);
            expect(complaint.result?.rated).toBe(true);
            expect(complaint.result?.assessmentType).toBe(AssessmentType.MANUAL);
            expect(complaint.result?.assessor?.login).toBe('tutor1');
            expect(complaint.result?.feedbacks).toHaveLength(1);
            expect(complaint.result?.submission?.id).toBe(21);
            const participation = complaint.result?.submission?.participation as StudentParticipation;
            expect(participation.id).toBe(31);
            expect(participation.exercise?.id).toBe(41);
            expect(participation.exercise?.type).toBe(ExerciseType.TEXT);
            expect(participation.exercise?.title).toBe('My Exercise');
        });

        it('should keep assessorKey and assessorLabel when result.assessor is redacted', () => {
            const dto: ComplaintDTO = {
                id: 13,
                complaintType: 'COMPLAINT',
                assessorKey: '42',
                assessorLabel: 'Foreign Tutor',
                result: { id: 56, score: 70, rated: true, assessmentType: 'MANUAL' },
            };

            const complaint = complaintService.convertComplaintFromServerInList(dto);

            expect(complaint.assessorKey).toBe('42');
            expect(complaint.assessorLabel).toBe('Foreign Tutor');
            expect(complaint.result?.assessor).toBeUndefined();
        });

        it('should keep the test case a feedback of the complained result names', () => {
            let received: Complaint[] | undefined;
            complaintService.findAll({ exerciseId: 7, complaintType: 'COMPLAINT' }).subscribe((complaints) => (received = complaints));

            expectGet().request.flush([
                {
                    id: 14,
                    result: {
                        id: 57,
                        feedbacks: [{ id: 2, type: 'AUTOMATIC', credits: 1, positive: false, visibility: 'AFTER_DUE_DATE', testCaseName: 'testSort()', text: 'failed' }],
                    },
                } satisfies ComplaintDTO,
            ]);

            const feedback = received?.[0].result?.feedbacks?.[0];
            expect(feedback).toBeInstanceOf(Feedback);
            expect(feedback?.type).toBe(FeedbackType.AUTOMATIC);
            expect(feedback?.testCase).toEqual({ testName: 'testSort()', visibility: Visibility.AfterDueDate });
            expect(Feedback.isTestCaseFeedback(feedback!)).toBe(true);
        });
    });
});
