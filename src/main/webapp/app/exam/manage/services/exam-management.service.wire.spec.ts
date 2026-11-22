import { TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { AccountService } from 'app/core/auth/account.service';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

/**
 * Pins the wire contract of the exam management service: the same server JSON must give the same client object, and the same form state the same request.
 * The fixtures list every field the server sends (see the response DTOs under exam/dto); the expectations are separate copies of them.
 */

const DATE = (day: number) => '2026-03-0' + day + 'T10:00:00.000Z';
const EXAM_DATE_KEYS = [
    'visibleDate',
    'startDate',
    'endDate',
    'publishResultsDate',
    'examStudentReviewStart',
    'examStudentReviewEnd',
    'exampleSolutionPublicationDate',
    'examSummaryPublicationDate',
] as const;

const courseDto = () => ({ id: 7, title: 'Programming', testCourse: false });

/** ExamDTO */
const examDto = () => ({
    id: 5,
    title: 'Final exam',
    examMode: 'REAL',
    examWithAttendanceCheck: false,
    visibleDate: DATE(1),
    startDate: DATE(2),
    endDate: DATE(3),
    publishResultsDate: DATE(4),
    examStudentReviewStart: DATE(5),
    examStudentReviewEnd: DATE(6),
    gracePeriod: 180,
    workingTime: 7200,
    startText: 'Start',
    endText: 'End',
    confirmationStartText: 'Confirm start',
    confirmationEndText: 'Confirm end',
    examMaxPoints: 100,
    randomizeExerciseOrder: false,
    numberOfExercisesInExam: 3,
    numberOfCorrectionRoundsInExam: 2,
    examiner: 'Prof. Example',
    moduleNumber: 'IN0001',
    courseName: 'Programming',
    exampleSolutionPublicationDate: DATE(7),
    examSummaryPublicationDate: DATE(8),
    course: courseDto(),
    channelName: 'exam-final',
    examArchivePath: 'archives/final.zip',
});

/** ExamExerciseDTO of one exercise per type, with the details of the detailed and import reads */
const examExercises = () => [
    {
        id: 11,
        type: 'programming',
        title: 'Sorting',
        maxPoints: 20,
        bonusPoints: 2,
        difficulty: 'MEDIUM',
        includedInOverallScore: 'INCLUDED_COMPLETELY',
        assessmentType: 'AUTOMATIC',
        teamMode: false,
        testRunParticipationsExist: true,
        numberOfParticipations: 4,
        shortName: 'sorting',
        projectKey: 'SORT',
        allowOfflineIde: true,
        allowOnlineEditor: false,
        allowOnlineIde: false,
        templateParticipation: { type: 'template', buildPlanId: 'SORT-BASE' },
        solutionParticipation: { type: 'solution', buildPlanId: 'SORT-SOLUTION' },
    },
    {
        id: 12,
        type: 'quiz',
        title: 'Quiz',
        maxPoints: 10,
        bonusPoints: 0,
        includedInOverallScore: 'INCLUDED_AS_BONUS',
        assessmentType: 'AUTOMATIC',
        teamMode: false,
        quizQuestions: [
            { id: 101, type: 'multiple-choice' },
            { id: 102, type: 'drag-and-drop' },
            { id: 103, type: 'short-answer' },
        ],
        randomizeQuestionOrder: true,
        allowedNumberOfAttempts: 2,
        quizMode: 'SYNCHRONIZED',
        duration: 600,
    },
    {
        id: 13,
        type: 'modeling',
        title: 'Diagram',
        maxPoints: 15,
        difficulty: 'HARD',
        includedInOverallScore: 'NOT_INCLUDED',
        assessmentType: 'SEMI_AUTOMATIC',
        teamMode: false,
        diagramType: 'ClassDiagram',
    },
    { id: 14, type: 'text', title: 'Essay', maxPoints: 5, includedInOverallScore: 'INCLUDED_COMPLETELY', assessmentType: 'MANUAL', teamMode: true, shortName: 'essay' },
    {
        id: 15,
        type: 'file-upload',
        title: 'Upload',
        maxPoints: 5,
        includedInOverallScore: 'INCLUDED_COMPLETELY',
        assessmentType: 'MANUAL',
        teamMode: false,
        filePattern: 'pdf,png',
    },
];

/** ExamWithExerciseGroupsDTO */
const examWithGroupsDto = () => ({
    ...examDto(),
    channelName: undefined,
    started: true,
    numberOfExamUsers: 42,
    exerciseGroups: [
        { id: 21, title: 'Group 1', isMandatory: true, exercises: examExercises() },
        { id: 22, title: 'Group 2', isMandatory: false },
    ],
});

/** ExamForAssessmentDashboardDTO */
const dashboardDto = () => ({
    id: 5,
    title: 'Final exam',
    endDate: DATE(3),
    publishResultsDate: DATE(4),
    numberOfCorrectionRoundsInExam: 2,
    course: { id: 7, complaintsEnabled: true, requestMoreFeedbackEnabled: false },
    exerciseGroups: [
        {
            id: 21,
            title: 'Group 1',
            exercises: [
                {
                    id: 11,
                    type: 'text',
                    title: 'Essay',
                    releaseDate: DATE(1),
                    includedInOverallScore: 'INCLUDED_COMPLETELY',
                    teamMode: false,
                    allowComplaintsForAutomaticAssessments: true,
                    secondCorrectionEnabled: true,
                    averageRating: 4.5,
                    numberOfRatings: 3,
                    numberOfSubmissions: { inTime: 10, late: 1 },
                    totalNumberOfAssessments: { inTime: 8, late: 1 },
                    numberOfAssessmentsOfCorrectionRounds: [
                        { inTime: 8, late: 1 },
                        { inTime: 2, late: 0 },
                    ],
                    numberOfComplaints: 2,
                    numberOfOpenComplaints: 1,
                    numberOfMoreFeedbackRequests: 4,
                    numberOfOpenMoreFeedbackRequests: 3,
                    tutorParticipations: [{ id: 31, status: 'TRAINED', trainedExampleSubmissions: [{ id: 41, usedForTutorial: true }] }],
                    exampleSubmissions: [{ id: 42, usedForTutorial: false }],
                },
            ],
        },
    ],
});

/** ExamScoresDTO */
const scoresDto = () => ({
    examId: 5,
    title: 'Final exam',
    maxPoints: 100,
    averagePointsAchieved: 61.5,
    hasSecondCorrectionAndStarted: true,
    exerciseGroups: [
        {
            id: 21,
            title: 'Group 1',
            maxPoints: 20,
            numberOfParticipants: 4,
            containedExercises: [{ exerciseId: 11, title: 'Sorting', maxPoints: 20, numberOfParticipants: 4, exerciseType: 'ProgrammingExercise' }],
        },
    ],
    studentResults: [
        {
            userId: 1,
            name: 'Ada Lovelace',
            email: 'ada@example.org',
            login: 'ada',
            registrationNumber: '0100',
            submitted: true,
            overallPointsAchieved: 80,
            overallScoreAchieved: 80,
            overallGrade: '1.7',
            overallGradeInFirstCorrection: '2.0',
            hasPassed: true,
            overallPointsAchievedInFirstCorrection: 75,
            gradeWithBonus: { studentPointsOfBonusSource: 4, bonusGrade: '1.3', finalGrade: '1.3', bonusStrategy: 'GRADE_JUMP', mostSeverePlagiarismVerdict: 'NONE' },
            exerciseGroupIdToExerciseResult: { '21': { exerciseId: 11, title: 'Sorting', maxScore: 100, achievedScore: 80, achievedPoints: 16, hasNonEmptySubmission: true } },
            mostSeverePlagiarismVerdict: 'NONE',
            maxPoints: 100,
        },
    ],
});

/** Normalizes a client value for comparison: dayjs dates compare by their ISO string. */
function plain(value: unknown): unknown {
    const copy = JSON.parse(JSON.stringify(value));
    const strip = (node: unknown): void => {
        if (Array.isArray(node)) {
            node.forEach(strip);
        } else if (node && typeof node === 'object') {
            const record = node as Record<string, unknown>;
            Object.values(record).forEach(strip);
        }
    };
    strip(copy);
    return copy;
}

/** The generated client requests the same paths with a leading slash, which the browser resolves to the same URL. */
function withoutLeadingSlash(url: string): string {
    return url.replace(/^\//, '');
}

/** The query of a request, wherever the client puts it: in the params or in the URL. */
function query(request: TestRequest): URLSearchParams {
    return new URL(request.request.urlWithParams, 'http://localhost').searchParams;
}

/** The body of a response, whether the method returns the HttpResponse or the body itself. */
function bodyOf<T>(result: unknown): T {
    return (result instanceof HttpResponse ? result.body : result) as T;
}

describe('Exam management wire contract', () => {
    let service: ExamManagementService;
    let studentExamService: StudentExamService;
    let httpMock: HttpTestingController;
    let accountService: AccountService;
    let setTitle: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: WebsocketService, useClass: MockWebsocketService },
            ],
        });
        service = TestBed.inject(ExamManagementService);
        studentExamService = TestBed.inject(StudentExamService);
        httpMock = TestBed.inject(HttpTestingController);
        accountService = TestBed.inject(AccountService);
        vi.spyOn(accountService, 'setAccessRightsForCourse');
        setTitle = vi.spyOn(TestBed.inject(EntityTitleService), 'setTitle');
    });

    afterEach(() => {
        httpMock.verify();
        vi.useRealTimers();
    });

    /** Answers the only open request with a fresh copy of the fixture, so the code under test cannot change the fixture. */
    function answer(method: string, url: string, fixture: unknown): TestRequest {
        const request = httpMock.expectOne((req) => req.method === method && withoutLeadingSlash(req.urlWithParams) === url);
        request.flush(fixture === null ? null : JSON.parse(JSON.stringify(fixture)));
        return request;
    }

    function expectExamDates(exam: Exam | undefined, present: readonly string[] = EXAM_DATE_KEYS) {
        for (const key of EXAM_DATE_KEYS) {
            const value = exam?.[key];
            if (present.includes(key)) {
                expect(dayjs.isDayjs(value), key).toBe(true);
            } else {
                expect(value, key).toBeUndefined();
            }
        }
    }

    describe('exam reads', () => {
        it('maps an exam without exercise groups and registers its course and title', () => {
            let exam: Exam | undefined;
            service.find(7, 5).subscribe((result) => (exam = bodyOf<Exam>(result)));
            const request = answer('GET', 'api/exam/courses/7/exams/5?withExerciseGroups=false', examDto());
            expect(query(request).get('withExerciseGroups')).toBe('false');

            expect(plain(exam)).toEqual(examDto());
            expectExamDates(exam);
            expect(accountService.setAccessRightsForCourse).toHaveBeenCalledOnce();
            expect(vi.mocked(accountService.setAccessRightsForCourse).mock.calls[0][0]).toBe(exam!.course);
            expect(setTitle).toHaveBeenCalledExactlyOnceWith(EntityType.EXAM, [5], 'Final exam');
        });

        it('maps an exam with exercise groups of every exercise type and keeps every field', () => {
            let exam: Exam | undefined;
            service.find(7, 5, true).subscribe((result) => (exam = bodyOf<Exam>(result)));
            answer('GET', 'api/exam/courses/7/exams/5?withExerciseGroups=true', examWithGroupsDto());

            expect(plain(exam)).toEqual(examWithGroupsDto());
            expectExamDates(exam);
            const exercises = exam!.exerciseGroups![0].exercises!;
            expect(exercises.map((exercise) => exercise.type)).toEqual(['programming', 'quiz', 'modeling', 'text', 'file-upload']);
            expect(exam!.exerciseGroups![1].exercises).toBeUndefined();
            expect(vi.mocked(accountService.setAccessRightsForCourse).mock.calls[0][0]).toBe(exam!.course);
        });

        it('leaves the dates the server omits undefined and adds no class defaults', () => {
            let exam: Exam | undefined;
            service.find(7, 5).subscribe((result) => (exam = bodyOf<Exam>(result)));
            answer('GET', 'api/exam/courses/7/exams/5?withExerciseGroups=false', { id: 5, title: 'Slim' });

            expect(plain(exam)).toEqual({ id: 5, title: 'Slim' });
            expectExamDates(exam, []);
            expect(accountService.setAccessRightsForCourse).not.toHaveBeenCalled();
            expect(setTitle).toHaveBeenCalledExactlyOnceWith(EntityType.EXAM, [5], 'Slim');
        });

        it('maps the exam of the import dialog', () => {
            let exam: Exam | undefined;
            service.findWithExercisesAndWithoutCourseId(5).subscribe((result) => (exam = bodyOf<Exam>(result)));
            answer('GET', 'api/exam/exams/5', examWithGroupsDto());

            expect(plain(exam)).toEqual(examWithGroupsDto());
            expectExamDates(exam);
        });

        it('maps the exams of a course', () => {
            let exams: Exam[] | undefined;
            service.findAllExamsForCourse(7).subscribe((result) => (exams = bodyOf<Exam[]>(result)));
            answer('GET', 'api/exam/courses/7/exams', [examWithGroupsDto(), { ...examWithGroupsDto(), id: 6, title: 'Second', exerciseGroups: undefined }]);

            expect(plain(exams)).toEqual([examWithGroupsDto(), { ...examWithGroupsDto(), id: 6, title: 'Second', exerciseGroups: undefined }]);
            exams!.forEach((exam) => expectExamDates(exam));
            expect(setTitle).toHaveBeenCalledTimes(2);
            expect(setTitle).toHaveBeenNthCalledWith(2, EntityType.EXAM, [6], 'Second');
            expect(vi.mocked(accountService.setAccessRightsForCourse).mock.calls.map((call) => call[0])).toEqual(exams!.map((exam) => exam.course));
        });

        it('maps the exams that the user can use for quiz questions', () => {
            let exams: Exam[] | undefined;
            service.findAllExamsAccessibleToUser(7).subscribe((result) => (exams = bodyOf<Exam[]>(result)));
            answer('GET', 'api/exam/courses/7/exams-for-user', [
                { id: 5, title: 'Final exam' },
                { id: 6, title: 'Second' },
            ]);

            expect(plain(exams)).toEqual([
                { id: 5, title: 'Final exam' },
                { id: 6, title: 'Second' },
            ]);
            expect(setTitle).toHaveBeenCalledTimes(2);
        });

        it('maps the current and upcoming exams of all courses', () => {
            const upcoming = [
                { id: 5, title: 'Final exam', examMode: 'TEST', course: { id: 7, title: 'Programming' }, visibleDate: DATE(1), startDate: DATE(2), endDate: DATE(3) },
            ];
            let exams: Exam[] | undefined;
            service.findAllCurrentAndUpcomingExams().subscribe((result) => (exams = bodyOf<Exam[]>(result)));
            answer('GET', 'api/exam/admin/courses/upcoming-exams', upcoming);

            expect(plain(exams)).toEqual(upcoming);
            expectExamDates(exams![0], ['visibleDate', 'startDate', 'endDate']);
            expect(exams![0].examMode).toBe(ExamMode.TEST);
            expect(vi.mocked(accountService.setAccessRightsForCourse).mock.calls[0][0]).toBe(exams![0].course);
        });

        it.each([
            [false, 'exam-for-assessment-dashboard'],
            [true, 'exam-for-test-run-assessment-dashboard'],
        ])('maps the exam of the assessment dashboard (test run: %s)', (isTestRun, path) => {
            let exam: Exam | undefined;
            service.getExamWithInterestingExercisesForAssessmentDashboard(7, 5, isTestRun).subscribe((result) => (exam = bodyOf<Exam>(result)));
            answer('GET', 'api/exam/courses/7/exams/5/' + path, dashboardDto());

            expect(plain(exam)).toEqual(plain(dashboardDto()));
            expectExamDates(exam, ['endDate', 'publishResultsDate']);
            // the exercise dates stay as the server sent them
            expect(exam!.exerciseGroups![0].exercises![0].releaseDate).toBe(DATE(1));
            expect(vi.mocked(accountService.setAccessRightsForCourse).mock.calls[0][0]).toBe(exam!.course);
        });

        it('maps the exam after a reset', () => {
            const reset = {
                ...examWithGroupsDto(),
                exerciseGroups: [
                    { id: 21, title: 'Group 1', isMandatory: true, exercises: [{ id: 11, type: 'programming', title: 'Sorting', maxPoints: 20, projectKey: 'SORT' }] },
                ],
            };
            let exam: Exam | undefined;
            service.reset(7, 5).subscribe((result) => (exam = bodyOf<Exam>(result)));
            answer('DELETE', 'api/exam/courses/7/exams/5/reset', reset);

            expect(plain(exam)).toEqual(reset);
            expectExamDates(exam);
        });

        it('maps the working time update', () => {
            let exam: Exam | undefined;
            service.updateWorkingTime(7, 5, -600).subscribe((result) => (exam = bodyOf<Exam>(result)));
            const request = answer('PATCH', 'api/exam/courses/7/exams/5/working-time', examDto());

            expect(request.request.body).toBe(-600);
            expect(plain(exam)).toEqual(examDto());
            expectExamDates(exam);
        });
    });

    describe('other reads', () => {
        it('returns the exam scores as the server sends them', () => {
            let scores: unknown;
            service.getExamScores(7, 5).subscribe((result) => (scores = bodyOf(result)));
            answer('GET', 'api/exam/courses/7/exams/5/scores', scoresDto());

            expect(plain(scores)).toEqual(scoresDto());
        });

        it('reads the latest end date as dayjs', () => {
            let information: { latestIndividualEndDate?: dayjs.Dayjs } | undefined;
            service.getLatestIndividualEndDateOfExam(7, 5).subscribe((result) => (information = bodyOf(result)));
            answer('GET', 'api/exam/courses/7/exams/5/latest-end-date', { latestIndividualEndDate: DATE(3) });

            expect(dayjs.isDayjs(information!.latestIndividualEndDate)).toBe(true);
            expect(information!.latestIndividualEndDate!.toISOString()).toBe(DATE(3));
        });

        it('turns an absent latest end date into the current time', () => {
            vi.useFakeTimers();
            vi.setSystemTime(new Date(DATE(5)));
            let information: { latestIndividualEndDate?: dayjs.Dayjs } | undefined;
            service.getLatestIndividualEndDateOfExam(7, 5).subscribe((result) => (information = bodyOf(result)));
            answer('GET', 'api/exam/courses/7/exams/5/latest-end-date', {});

            expect(information!.latestIndividualEndDate!.toISOString()).toBe(DATE(5));
        });

        it('converts the start date of the exercise start status', () => {
            let status: unknown;
            service.getExerciseStartStatus(7, 5).subscribe((result) => (status = bodyOf(result)));
            answer('GET', 'api/exam/courses/7/exams/5/student-exams/start-exercises/status', { finished: 3, failed: 1, overall: 10, participationCount: 12, startedAt: DATE(2) });

            expect(plain(status)).toEqual({ finished: 3, failed: 1, overall: 10, participationCount: 12, startedAt: DATE(2) });
            expect(dayjs.isDayjs((status as { startedAt: unknown }).startedAt)).toBe(true);
        });

        it('keeps an absent start of the exercise start status empty', () => {
            let status: { startedAt?: unknown } | undefined;
            service.getExerciseStartStatus(7, 5).subscribe((result) => (status = bodyOf(result)));
            answer('GET', 'api/exam/courses/7/exams/5/student-exams/start-exercises/status', { finished: 0, failed: 0, overall: 0, participationCount: 0, startedAt: null });

            expect(status!.startedAt).toBeUndefined();
        });

        it('reconnects the results of the locked submissions', () => {
            const locked = [
                {
                    id: 51,
                    submissionDate: DATE(2),
                    submissionExerciseType: 'text',
                    participation: { id: 61, exercise: { id: 11, type: 'text', title: 'Essay', exerciseGroup: { id: 21 } }, submissionCount: 2 },
                    results: [
                        { id: 71, score: 50, completionDate: DATE(3), correctionRound: 0 },
                        { id: 72, score: 60, completionDate: DATE(4), correctionRound: 1 },
                    ],
                },
            ];
            let submissions: { results?: { id?: number; submission?: unknown }[]; latestResult?: { id?: number } }[] | undefined;
            service.findAllLockedSubmissionsOfExam(7, 5).subscribe((result) => (submissions = bodyOf(result)));
            answer('GET', 'api/exam/courses/7/exams/5/locked-submissions', locked);

            const [submission] = submissions!;
            expect(submission.results!.map((result) => result.id)).toEqual([71, 72]);
            expect(submission.results![1].submission).toBe(submission);
            expect(submission.results![0].submission).toBeUndefined();
            expect(
                plain(
                    Object.assign({}, submission, { results: submission.results!.map((result) => Object.assign({}, result, { submission: undefined })), latestResult: undefined }),
                ),
            ).toEqual(locked[0]);
        });

        it('emits nothing for a locked submission response without a body', () => {
            const emitted = vi.fn();
            service.findAllLockedSubmissionsOfExam(7, 5).subscribe({ next: emitted });
            answer('GET', 'api/exam/courses/7/exams/5/locked-submissions', null);
            expect(emitted).not.toHaveBeenCalled();
        });

        it('returns the exercises with potential plagiarism as the server sends them', () => {
            const exercises = [{ id: 11, title: 'Essay', type: 'text', exerciseGroup: { id: 21, exam: { id: 5 } } }];
            let result: unknown;
            service.getExercisesWithPotentialPlagiarismForExam(7, 5).subscribe((value) => (result = value));
            answer('GET', 'api/exam/courses/7/exams/5/exercises-with-potential-plagiarism', exercises);
            expect(plain(result)).toEqual(exercises);
        });

        it('returns the announcements with a dayjs creation date', () => {
            const announcements = [
                { eventType: 'examWideAnnouncement', id: 1, createdDate: DATE(2), text: 'Hello' },
                { eventType: 'examWideAnnouncement', id: 2, createdDate: DATE(3) },
            ];
            let result: unknown;
            service.getAnnouncements(7, 5).subscribe((value) => (result = value));
            answer('GET', 'api/exam/courses/7/exams/5/announcements', announcements);

            expect(plain(result)).toEqual(announcements);
            (result as { createdDate: unknown }[]).forEach((event) => expect(dayjs.isDayjs(event.createdDate)).toBe(true));
        });

        it('posts the announcement text as a bare string and returns the event with a dayjs date', () => {
            let result: { createdDate?: unknown } | undefined;
            service.createAnnouncement(7, 5, 'Please read').subscribe((value) => (result = value));
            const request = answer('POST', 'api/exam/courses/7/exams/5/announcements', { eventType: 'examWideAnnouncement', id: 3, createdDate: DATE(2), text: 'Please read' });

            expect(request.request.body).toBe('Please read');
            expect(request.request.serializeBody()).toBe('Please read');
            expect(request.request.detectContentTypeHeader()).toBe('text/plain');
            expect(plain(result)).toEqual({ eventType: 'examWideAnnouncement', id: 3, createdDate: DATE(2), text: 'Please read' });
            expect(dayjs.isDayjs(result!.createdDate)).toBe(true);
        });
    });

    describe('student lists', () => {
        const row = () => ({
            id: 1,
            userId: 9,
            login: 'ada',
            name: 'Ada Lovelace',
            visibleRegistrationNumber: '0100',
            studentImagePath: 'img.png',
            plannedRoom: 'A',
            actualRoom: 'A',
            plannedSeat: '1',
            actualSeat: '1',
            didCheckImage: true,
            didCheckName: true,
            didCheckLogin: false,
            didCheckRegistrationNumber: false,
            signingImagePath: 'sign.png',
            didExamUserAttendExam: true,
            studentExamId: 3,
            workingTime: 7200,
            started: true,
            submitted: true,
            startedDate: DATE(2),
            submissionDate: DATE(3),
            numberOfExamSessions: 2,
            progress: 'submitted',
        });
        const search = { page: 2, pageSize: 25, sortingOrder: SortingOrder.DESCENDING, sortedColumn: 'login', searchTerm: '' };

        it('maps a page of exam students and reads the total from the header', () => {
            let page: unknown;
            service.findExamStudentsPaged(7, 5, search).subscribe((result) => (page = result));
            const request = httpMock.expectOne((req) => withoutLeadingSlash(req.urlWithParams.split('?')[0]) === 'api/exam/courses/7/exams/5/exam-students/paged');
            expect(request.request.method).toBe('GET');
            expect(Object.fromEntries(query(request))).toEqual({
                page: '2',
                pageSize: '25',
                sortingOrder: 'DESCENDING',
                sortedColumn: 'login',
                searchTerm: '',
            });
            request.flush([row(), { id: 2, progress: 'inconsistent' }, { id: 4, progress: 'somethingNew' }, { userId: 5 }], { headers: { 'X-Total-Count': '4' } });

            expect(plain(page)).toEqual({ content: [row(), { id: 2, progress: 'inconsistent' }, { id: 4, progress: 'somethingNew' }, { userId: 5 }], totalElements: 4 });
            const content = (page as { content: { startedDate?: unknown; submissionDate?: unknown }[] }).content;
            expect(dayjs.isDayjs(content[0].startedDate)).toBe(true);
            expect(dayjs.isDayjs(content[0].submissionDate)).toBe(true);
            expect(content[1].startedDate).toBeUndefined();
        });

        it('sends the filter property only when it is set', () => {
            service.findExamStudentsPaged(7, 5, { ...search, filterProp: 'started' }).subscribe();
            const request = httpMock.expectOne((req) => withoutLeadingSlash(req.urlWithParams.split('?')[0]) === 'api/exam/courses/7/exams/5/exam-students/paged');
            expect(query(request).get('filterProp')).toBe('started');
            request.flush([]);
        });

        it('reads a missing total header as zero', () => {
            let page: unknown;
            service.findExamStudentsPaged(7, 5, search).subscribe((result) => (page = result));
            httpMock.expectOne((req) => withoutLeadingSlash(req.urlWithParams.split('?')[0]) === 'api/exam/courses/7/exams/5/exam-students/paged').flush([]);
            expect(page).toEqual({ content: [], totalElements: 0 });
        });

        it('searches the users for the registration, with the term even when empty', () => {
            const users = [
                { id: 1, login: 'ada', name: 'Ada Lovelace', email: 'ada@example.org', registrationNumber: '0100', profilePictureUrl: '/p.png', isRegistered: true },
                { id: 2, login: 'bob', name: 'Bob', isRegistered: false },
                { login: 'noid', name: 'No Id', isRegistered: false },
                { id: 4, login: 'incomplete' },
            ];
            let result: unknown;
            service.searchUsersForExamRegistration(7, 5, '', 0, 10).subscribe((value) => (result = value));
            const request = httpMock.expectOne((req) => withoutLeadingSlash(req.urlWithParams.split('?')[0]) === 'api/exam/courses/7/exams/5/students/search');
            expect(request.request.method).toBe('GET');
            expect(Object.fromEntries(query(request))).toEqual({ searchTerm: '', page: '0', size: '10' });
            request.flush(users, { headers: { 'X-Total-Count': '4' } });

            expect(plain(result)).toEqual({ content: users, totalElements: 4 });
        });
    });

    describe('request bodies', () => {
        /** The exam as the update component hands it over: dayjs dates, an exercise group list for imports. */
        const formExam = (): Exam => {
            const exam = new Exam();
            exam.id = 5;
            exam.title = 'Final exam';
            exam.examMode = ExamMode.TEST;
            exam.examWithAttendanceCheck = true;
            exam.visibleDate = dayjs(DATE(1));
            exam.startDate = dayjs(DATE(2));
            exam.endDate = dayjs(DATE(3));
            exam.publishResultsDate = dayjs(DATE(4));
            exam.examStudentReviewStart = dayjs(DATE(5));
            exam.examStudentReviewEnd = dayjs(DATE(6));
            exam.exampleSolutionPublicationDate = dayjs(DATE(7));
            exam.examSummaryPublicationDate = dayjs(DATE(8));
            exam.gracePeriod = 120;
            exam.workingTime = 3600;
            exam.startText = 'Start';
            exam.endText = 'End';
            exam.confirmationStartText = 'Confirm start';
            exam.confirmationEndText = 'Confirm end';
            exam.examMaxPoints = 90;
            exam.randomizeExerciseOrder = true;
            exam.numberOfExercisesInExam = 2;
            exam.numberOfCorrectionRoundsInExam = 2;
            exam.examiner = 'Prof. Example';
            exam.moduleNumber = 'IN0001';
            exam.courseName = 'Programming';
            exam.channelName = 'exam-final';
            exam.course = { id: 7 } as Course;
            return exam;
        };
        const expectedUpdateBody = () => ({
            id: 5,
            title: 'Final exam',
            examMode: 'TEST',
            examWithAttendanceCheck: true,
            visibleDate: DATE(1),
            startDate: DATE(2),
            endDate: DATE(3),
            publishResultsDate: DATE(4),
            examStudentReviewStart: DATE(5),
            examStudentReviewEnd: DATE(6),
            gracePeriod: 120,
            workingTime: 3600,
            startText: 'Start',
            endText: 'End',
            confirmationStartText: 'Confirm start',
            confirmationEndText: 'Confirm end',
            examMaxPoints: 90,
            randomizeExerciseOrder: true,
            numberOfExercisesInExam: 2,
            numberOfCorrectionRoundsInExam: 2,
            examiner: 'Prof. Example',
            moduleNumber: 'IN0001',
            courseName: 'Programming',
            exampleSolutionPublicationDate: DATE(7),
            examSummaryPublicationDate: DATE(8),
            channelName: 'exam-final',
        });

        it('creates an exam with the update body and maps the response', () => {
            let exam: Exam | undefined;
            const created = formExam();
            created.id = undefined;
            service.create(7, created).subscribe((result) => (exam = bodyOf<Exam>(result)));
            const request = answer('POST', 'api/exam/courses/7/exams', examDto());

            const { id, ...expected } = expectedUpdateBody();
            expect(id).toBe(5);
            expect(JSON.parse(request.request.serializeBody() as string)).toEqual(expected);
            expect(plain(exam)).toEqual(examDto());
            expectExamDates(exam);
            expect(vi.mocked(accountService.setAccessRightsForCourse).mock.calls[0][0]).toBe(exam!.course);
        });

        it('updates an exam with the update body and maps the response', () => {
            let exam: Exam | undefined;
            service.update(7, formExam()).subscribe((result) => (exam = bodyOf<Exam>(result)));
            const request = answer('PUT', 'api/exam/courses/7/exams', examDto());

            expect(JSON.parse(request.request.serializeBody() as string)).toEqual(expectedUpdateBody());
            expect(plain(exam)).toEqual(examDto());
        });

        it('defaults the mode, the attendance check and the working time of an update body', () => {
            const exam = new Exam();
            exam.id = 5;
            exam.title = 'Minimal';
            exam.visibleDate = dayjs(DATE(1));
            exam.startDate = dayjs(DATE(2));
            exam.endDate = dayjs(DATE(3));
            exam.examMode = undefined;
            exam.examWithAttendanceCheck = undefined;
            exam.workingTime = undefined;
            exam.examMaxPoints = undefined;
            exam.numberOfCorrectionRoundsInExam = undefined;
            exam.randomizeExerciseOrder = undefined;
            service.update(7, exam).subscribe();
            const request = answer('PUT', 'api/exam/courses/7/exams', examDto());

            expect(JSON.parse(request.request.serializeBody() as string)).toEqual({
                id: 5,
                title: 'Minimal',
                examMode: 'REAL',
                examWithAttendanceCheck: false,
                visibleDate: DATE(1),
                startDate: DATE(2),
                endDate: DATE(3),
                workingTime: 0,
            });
        });

        it('imports an exam with the import body, the import id and the exercise groups', () => {
            const exam = formExam();
            const group = new ExerciseGroup();
            group.title = 'Group 1';
            group.isMandatory = undefined;
            const exercise = { id: 11, type: ExerciseType.TEXT, title: 'Essay', shortName: 'essay', maxPoints: 5, bonusPoints: 1, problemStatement: 'ignored' } as Exercise;
            group.exercises = [exercise];
            const second = new ExerciseGroup();
            second.title = 'Group 2';
            second.isMandatory = false;
            exam.exerciseGroups = [group, second];
            let imported: unknown;
            service.import(7, exam, 'abc').subscribe((result) => (imported = bodyOf(result)));
            const request = httpMock.expectOne((req) => withoutLeadingSlash(req.urlWithParams.split('?')[0]) === 'api/exam/courses/7/exam-import');
            expect(request.request.method).toBe('POST');
            expect(query(request).get('importId')).toBe('abc');
            expect([...query(request).keys()]).toEqual(['importId']);
            const { id, ...expected } = expectedUpdateBody();
            expect(id).toBe(5);
            expect(JSON.parse(request.request.serializeBody() as string)).toEqual({
                ...expected,
                courseId: 7,
                exerciseGroups: [
                    { title: 'Group 1', isMandatory: true, exercises: [{ id: 11, exerciseType: 'text', title: 'Essay', shortName: 'essay', maxPoints: 5, bonusPoints: 1 }] },
                    { title: 'Group 2', isMandatory: false },
                ],
            });
            request.flush({ exam: { id: 9, title: 'Imported' }, skippedExercises: ['A'], incompleteExercises: ['B'] });

            expect(plain(imported)).toEqual({ exam: { id: 9, title: 'Imported' }, skippedExercises: ['A'], incompleteExercises: ['B'] });
            expect(setTitle).toHaveBeenCalledExactlyOnceWith(EntityType.EXAM, [9], 'Imported');
        });

        it('imports an exam without exercise groups with no group list', () => {
            service.import(7, formExam(), 'abc').subscribe();
            const request = httpMock.expectOne((req) => withoutLeadingSlash(req.urlWithParams.split('?')[0]) === 'api/exam/courses/7/exam-import');
            expect('exerciseGroups' in JSON.parse(request.request.serializeBody() as string)).toBe(false);
            request.flush({ exam: { id: 9, title: 'Imported' } });
        });

        it('imports exercise groups into an exam', () => {
            const group = new ExerciseGroup();
            group.title = 'Group 1';
            group.isMandatory = true;
            group.exercises = [{ id: 11, type: ExerciseType.PROGRAMMING, title: 'Sorting', shortName: 'sorting', maxPoints: 20, bonusPoints: 0 } as Exercise];
            let imported: unknown;
            service.importExerciseGroup(7, 5, [group], 'xyz').subscribe((result) => (imported = bodyOf(result)));
            const request = httpMock.expectOne((req) => withoutLeadingSlash(req.urlWithParams.split('?')[0]) === 'api/exam/courses/7/exams/5/import-exercise-group');
            expect(request.request.method).toBe('POST');
            expect(query(request).get('importId')).toBe('xyz');
            expect(JSON.parse(request.request.serializeBody() as string)).toEqual([
                {
                    title: 'Group 1',
                    isMandatory: true,
                    exercises: [{ id: 11, exerciseType: 'programming', title: 'Sorting', shortName: 'sorting', maxPoints: 20, bonusPoints: 0 }],
                },
            ]);
            const response = {
                exerciseGroups: [{ id: 21, title: 'Group 1', isMandatory: true, exercises: [{ id: 11, type: 'programming', title: 'Sorting' }] }],
                skippedExercises: ['A'],
            };
            request.flush(response);
            expect(plain(imported)).toEqual(response);
        });
    });

    describe('student exam', () => {
        it('maps a student exam with grade and registers the course of its exam', () => {
            const studentExamWithGrade = {
                maxPoints: 100,
                maxBonusPoints: 10,
                gradeType: 'GRADE',
                studentExam: {
                    id: 3,
                    workingTime: 7200,
                    submitted: true,
                    started: true,
                    exam: { id: 5, title: 'Final exam', course: { id: 7, title: 'Programming', testCourse: false } },
                },
                studentResult: { userId: 1, name: 'Ada', login: 'ada', email: 'ada@example.org', registrationNumber: '0100', submitted: true, exerciseGroupIdToExerciseResult: {} },
                achievedPointsPerExercise: { '11': 16 },
                pendingExerciseIds: [11],
            };
            let result: unknown;
            studentExamService.find(7, 5, 3).subscribe((value) => (result = bodyOf(value)));
            answer('GET', 'api/exam/courses/7/exams/5/student-exams/3', studentExamWithGrade);

            expect(plain(result)).toEqual(studentExamWithGrade);
            const studentExam = (result as { studentExam: { exam: { course: unknown } } }).studentExam;
            expect(vi.mocked(accountService.setAccessRightsForCourse).mock.calls[0][0]).toBe(studentExam.exam.course);
        });

        it('does not register a course when the student exam has none', () => {
            studentExamService.find(7, 5, 3).subscribe();
            answer('GET', 'api/exam/courses/7/exams/5/student-exams/3', { maxPoints: 1, studentExam: { id: 3 } });
            expect(accountService.setAccessRightsForCourse).not.toHaveBeenCalled();
        });
    });
});
