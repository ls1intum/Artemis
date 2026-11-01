import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { take } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentExamWithGradeDTO, StudentResult } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { HttpErrorResponse, HttpHeaders, provideHttpClient } from '@angular/common/http';
import { ExamForOverview } from 'app/exam/shared/entities/exam-for-overview.model';

import { beforeEach, describe, expect, it, vi } from 'vitest';
describe('ExamParticipationService', () => {
    let service: ExamParticipationService;
    let httpMock: HttpTestingController;
    let exam: Exam;
    let studentExam: StudentExam;
    let localStorageService: LocalStorageService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), LocalStorageService, SessionStorageService, { provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(ExamParticipationService);
        httpMock = TestBed.inject(HttpTestingController);
        localStorageService = TestBed.inject(LocalStorageService);
        localStorageService.clear();

        exam = new Exam();
        studentExam = new StudentExam();
        studentExam.exercises = [];
    });

    it('should request explicit instructor-owner conduction with normal conversion', () => {
        let result: StudentExam | undefined;
        service.loadStudentExamWithExercisesForConduction(1, 2, 3, 'instructor-owner').subscribe((value) => (result = value));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush({ id: 3, exercises: [], exam: { startDate: '2026-10-25T08:00:00Z' } });
        expect(req.request.url).toBe('api/exam/courses/1/exams/2/student-exams/3/conduction/for-instructor');
        expect(dayjs.isDayjs(result?.exam?.startDate)).toBe(true);
    });

    it('should load a StudentExam with exercises for conduction', async () => {
        const returnedFromService = Object.assign({ started: true }, studentExam);
        service
            .loadStudentExamWithExercisesForConduction(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject(returnedFromService));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('should load a StudentExam with exercises for conduction of TestExam', async () => {
        const returnedFromService = Object.assign({ started: true }, studentExam);
        service
            .loadStudentExamWithExercisesForConduction(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject(returnedFromService));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('should load a StudentExam with exercises for summary', async () => {
        const returnedFromService = Object.assign({}, studentExam);
        service
            .loadStudentExamWithExercisesForSummary(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject(returnedFromService));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('should fall back to the locally cached exam when the conduction request fails', async () => {
        studentExam.id = 42;
        service.saveStudentExamToLocalStorage(1, 1, studentExam);

        let received: StudentExam | undefined;
        service
            .loadStudentExamWithExercisesForConduction(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => (received = resp));

        httpMock.expectOne({ method: 'GET' }).error(new ProgressEvent('error'), { status: 500 });

        expect(received).toBeDefined();
        expect(received!.id).toBe(42);
    });

    it('should propagate the error and not fall back to the locally cached exam when the summary request fails', async () => {
        studentExam.id = 42;
        service.saveStudentExamToLocalStorage(1, 1, studentExam);

        let received: StudentExam | undefined;
        let error: unknown;
        service
            .loadStudentExamWithExercisesForSummary(1, 1, 1)
            .pipe(take(1))
            .subscribe({ next: (resp) => (received = resp), error: (err) => (error = err) });

        httpMock.expectOne({ method: 'GET' }).error(new ProgressEvent('error'), { status: 500 });

        expect(received).toBeUndefined();
        expect(error).toBeInstanceOf(HttpErrorResponse);
    });

    it('should load a student exam grade info for summary', async () => {
        const studentExamWithGrade: StudentExamWithGradeDTO = {
            maxPoints: 100,
            maxBonusPoints: 10,
            studentResult: {} as StudentResult,
            gradeType: GradeType.GRADE,
            achievedPointsPerExercise: {
                1: 20,
                2: 10,
            },
        };
        const returnedFromService = Object.assign({}, studentExamWithGrade);
        service
            .loadStudentExamGradeInfoForSummary(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject(returnedFromService));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('should load a StudentExam', async () => {
        const sendExam = Object.assign(
            {
                visibleDate: dayjs(),
                startDate: dayjs(),
                endDate: dayjs(),
                publishResultDate: dayjs(),
                examStudentReviewStart: dayjs(),
                examStudentReviewEnd: dayjs(),
            },
            exam,
        );
        const returnedFromService = Object.assign(
            {
                exam: sendExam,
            },
            studentExam,
        );
        service
            .getOwnStudentExam(1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject(returnedFromService));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('should load a StudentExam for a TestExam', async () => {
        const sendExam = Object.assign(
            {
                visibleDate: dayjs(),
                startDate: dayjs(),
                endDate: dayjs(),
                publishResultDate: dayjs(),
                examStudentReviewStart: dayjs(),
                examStudentReviewEnd: dayjs(),
            },
            exam,
        );
        const returnedFromService = Object.assign(
            {
                exam: sendExam,
            },
            studentExam,
        );
        service
            .getOwnStudentExam(1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject(returnedFromService));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('should load only the exam overview fields and convert all sidebar dates', () => {
        const visibleDate = '2026-08-01T10:00:00.000Z';
        const startDate = '2026-08-02T10:00:00.000Z';
        const endDate = '2026-08-02T12:00:00.000Z';
        const serverExam = {
            id: 17,
            title: 'Overview exam',
            moduleNumber: 'M1',
            visibleDate,
            startDate,
            endDate,
            workingTime: 7200,
            examMaxPoints: 100,
            testExam: false,
        };
        let received: ExamForOverview[] | undefined;

        service.getExamsForOverview(5).subscribe((exams) => (received = exams));
        const request = httpMock.expectOne({ method: 'GET', url: 'api/exam/courses/5/exams-for-overview' });
        expect(request.request.params.keys()).toEqual([]);
        request.flush([serverExam]);

        expect(received).toHaveLength(1);
        expect(received?.[0]).toMatchObject({ id: 17, title: 'Overview exam', moduleNumber: 'M1', workingTime: 7200, examMaxPoints: 100, testExam: false });
        expect(dayjs.isDayjs(received?.[0].visibleDate)).toBe(true);
        expect(dayjs.isDayjs(received?.[0].startDate)).toBe(true);
        expect(dayjs.isDayjs(received?.[0].endDate)).toBe(true);
        expect(received?.[0].visibleDate?.toISOString()).toBe(visibleDate);
        expect(received?.[0].startDate?.toISOString()).toBe(startDate);
        expect(received?.[0].endDate?.toISOString()).toBe(endDate);
    });

    it('should load a StudentExam for a TestExam by Id', async () => {
        const sendExam = Object.assign(
            {
                visibleDate: dayjs(),
                startDate: dayjs(),
                endDate: dayjs(),
                publishResultDate: dayjs(),
                examStudentReviewStart: dayjs(),
                examStudentReviewEnd: dayjs(),
            },
            exam,
        );
        const returnedFromService = Object.assign(
            {
                exam: sendExam,
            },
            studentExam,
        );
        service
            .loadStudentExamWithExercisesForConduction(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject(returnedFromService));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('should load a StudentExam in the version of server', async () => {
        /*configure exercises of this student Exam*/
        const exercise = new TextExercise(new Course(), undefined);
        const studentParticipation = new StudentParticipation();
        const submission = new TextSubmission();
        submission.results = [new Result()];
        submission.participation = studentParticipation;
        getLatestSubmissionResult(submission)!.submission = submission;
        studentParticipation.submissions = [submission];
        exercise.studentParticipations = [studentParticipation];
        /*configure the exam of a student exam*/
        const examToSend = new Exam();

        const returnedFromService = Object.assign(studentExam, {
            exercises: [exercise],
            exam: examToSend,
        });
        let received: void | null = undefined;
        service
            .submitStudentExam(2, 2, returnedFromService)
            .pipe(take(1))
            .subscribe((resp) => {
                received = resp;
            });

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(null);
        expect(received).toBeNull();
    });

    it('should load testRun with exercises for conduction', async () => {
        const returnedFromService = Object.assign({}, studentExam);
        const expected = Object.assign({}, returnedFromService);
        service
            .loadTestRunWithExercisesForConduction(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject(expected));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('save examSessionToken to sessionStorage', async () => {
        service.saveExamSessionTokenToSessionStorage('token1');
        vi.spyOn(sessionStorage, 'setItem').mockImplementation(() => {
            expect(sessionStorage['ExamSessionToken']).toBe('token1');
        });
    });

    it('should save and load StudentExam from localStorage', async () => {
        studentExam.exercises = [];
        service.saveStudentExamToLocalStorage(1, 1, studentExam);

        service.loadStudentExamWithExercisesForConductionFromLocalStorage(1, 1).subscribe((localExam: StudentExam | undefined) => {
            expect(localExam).toBeDefined();
            expect(localExam).toEqual(studentExam);
        });
    });

    it('should load a List of StudentExams for a user and course', async () => {
        const returnedFromService = [studentExam];
        service
            .loadStudentExamsForTestExamsPerCourseAndPerUserForOverviewPage(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject(returnedFromService));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('should submit a StudentExam successfully', async () => {
        const studentExamCopy = Object.assign({}, studentExam);
        let received: void | null = undefined;
        service
            .submitStudentExam(1, 1, studentExamCopy)
            .pipe(take(1))
            .subscribe((resp) => {
                received = resp;
            });

        const req = httpMock.expectOne({ method: 'POST' });
        expect(req.request.url).toBe('api/exam/courses/1/exams/1/student-exams/submit');
        req.flush(null);
        expect(received).toBeNull();
    });
    it('should fetch sidebar data successfully', async () => {
        const returnedFromService = [studentExam];
        service.getRealExamSidebarData(1).subscribe((resp) => expect(resp).toMatchObject(returnedFromService));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('should throw error if submission is not in time', async () => {
        const errorHeaders = new HttpHeaders({ 'x-null-error': 'error.submissionNotInTime' });
        const errorResponse = new HttpErrorResponse({
            status: 403,
            headers: errorHeaders,
        });

        service
            .submitStudentExam(1, 1, studentExam)
            .pipe(take(1))
            .subscribe({
                error: (err) => expect(err.message).toBe('artemisApp.studentExam.submissionNotInTime'),
            });

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush('Submission Not In Time', errorResponse);
    });

    it('should throw error if the exam was already submitted', async () => {
        const errorHeaders = new HttpHeaders({ 'x-null-error': 'error.alreadySubmitted' });
        const errorResponse = new HttpErrorResponse({
            status: 409,
            headers: errorHeaders,
        });

        service
            .submitStudentExam(1, 1, studentExam)
            .pipe(take(1))
            .subscribe({
                error: (err) => expect(err.message).toBe('artemisApp.studentExam.alreadySubmitted'),
            });

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush('Already Submitted', errorResponse);
    });

    it('should throw a generic error if submission fails for any other reason', async () => {
        const errorResponse = new HttpErrorResponse({
            status: 500,
        });

        service
            .submitStudentExam(1, 1, studentExam)
            .pipe(take(1))
            .subscribe({
                error: (err) => expect(err.message).toBe('artemisApp.studentExam.handInFailed'),
            });

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush('Hand-in failed', errorResponse);
    });

    describe('Athena feedback for test exams', () => {
        it('should POST to the request-feedback endpoint with an empty body', () => {
            let nextCalled = false;
            service
                .requestAthenaFeedback(7, 8, 9)
                .pipe(take(1))
                .subscribe(() => (nextCalled = true));

            const req = httpMock.expectOne({ method: 'POST', url: 'api/exam/courses/7/exams/8/student-exams/9/request-feedback' });
            expect(req.request.body).toBeNull();
            req.flush(null);

            expect(nextCalled).toBe(true);
        });

        it('should GET the Athena feedback usage and return used/limit', () => {
            const usage = { used: 3, limit: 10 };
            let received: { used: number; limit: number } | undefined;
            service
                .getAthenaFeedbackUsage(7, 8, 9)
                .pipe(take(1))
                .subscribe((resp) => (received = resp));

            const req = httpMock.expectOne({ method: 'GET', url: 'api/exam/courses/7/exams/8/student-exams/9/athena-feedback-usage' });
            req.flush(usage);

            expect(received).toEqual(usage);
        });

        it('should propagate errors from the request-feedback endpoint', () => {
            let errorStatus: number | undefined;
            service
                .requestAthenaFeedback(1, 2, 3)
                .pipe(take(1))
                .subscribe({
                    error: (err: HttpErrorResponse) => (errorStatus = err.status),
                });

            const req = httpMock.expectOne({ method: 'POST', url: 'api/exam/courses/1/exams/2/student-exams/3/request-feedback' });
            req.flush('rate limit reached', new HttpErrorResponse({ status: 400 }));

            expect(errorStatus).toBe(400);
        });
    });
    it('caches a serializable exam clone while retaining the working graph back references', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.id = 7;
        const participation: StudentParticipation = { id: 8, exercise };
        const submission: TextSubmission = { id: 9, participation };
        const result: Result = { id: 10, submission };
        const feedback = { id: 11, result, credits: 2 };
        result.feedbacks = [feedback];
        submission.results = [result];
        participation.submissions = [submission];
        exercise.studentParticipations = [participation];
        studentExam.id = 6;
        studentExam.exercises = [exercise];
        service.saveStudentExamToLocalStorage(1, 2, studentExam);
        const cached = localStorageService.retrieve<StudentExam>('artemis_student_exam_1_2');
        const cachedParticipation = cached?.exercises?.[0].studentParticipations?.[0];
        expect(cachedParticipation?.id).toBe(8);
        expect(cachedParticipation?.exercise).toBeUndefined();
        expect(cachedParticipation?.submissions?.[0]).toMatchObject({ id: 9, results: [{ id: 10, feedbacks: [{ id: 11, credits: 2 }] }] });
        expect(cachedParticipation?.submissions?.[0].participation).toBeUndefined();
        expect(cachedParticipation?.submissions?.[0].results?.[0].submission).toBeUndefined();
        expect(cachedParticipation?.submissions?.[0].results?.[0].feedbacks?.[0].result).toBeUndefined();
        expect(submission.participation).toBe(participation);
        expect(result.submission).toBe(submission);
        expect(feedback.result).toBe(result);
        expect(participation.exercise).toBe(exercise);
    });

    it('keeps failed-save flags scoped to the course and exam', () => {
        expect(service.lastSaveFailed(1, 2)).toBe(false);
        service.setLastSaveFailed(true, 1, 2);
        expect(service.lastSaveFailed(1, 2)).toBe(true);
        expect(service.lastSaveFailed(1, 3)).toBe(false);
        service.setLastSaveFailed(false, 1, 2);
        expect(service.lastSaveFailed(1, 2)).toBe(false);
    });

    it.each([
        [true, true, 'synced'],
        [true, false, 'notSynced'],
        [false, true, 'notStarted'],
        [false, false, 'notSynced'],
    ] as const)('shows text submission state submitted=%s synced=%s', (submitted, isSynced, tooltip) => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.studentParticipations = [{ submissions: [{ submitted, isSynced }] }];
        expect(service.getExerciseButtonTooltip(exercise)).toBe(tooltip);
    });

    it('resets exam layout and maps exercises into sidebar cards', () => {
        const started: boolean[] = [],
            testRun: boolean[] = [],
            end: boolean[] = [],
            updates: boolean[] = [];
        const subscriptions = [
            service.examIsStarted$.subscribe((v) => started.push(v)),
            service.testRunStarted$.subscribe((v) => testRun.push(v)),
            service.endViewDisplayed$.subscribe((v) => end.push(v)),
            service.shouldUpdateTestExamsObservable.subscribe((v) => updates.push(v)),
        ];
        service.setExamLayout();
        service.setExamLayout(true, true);
        service.setEndView(true);
        service.setShouldUpdateTestExams(true);
        document.documentElement.style.setProperty('--header-height', '0px');
        service.resetExamLayout();
        expect(started).toEqual([false, true, true, false]);
        expect(testRun).toEqual([false, false, true, false]);
        expect(end).toEqual([false, true]);
        expect(updates).toEqual([false, true]);
        expect(document.documentElement.style.getPropertyValue('--header-height')).toBe('68px');
        const exercise = new TextExercise(undefined, undefined);
        exercise.id = 7;
        exercise.exerciseGroup = { title: 'Written response' };
        expect(service.mapExercisesToSidebarCardElements([exercise])).toEqual([expect.objectContaining({ title: 'Written response', id: 7, size: 'M' })]);
        expect(service.mapExerciseToSidebarCardElement(new TextExercise(undefined, undefined))).toMatchObject({ title: '', id: '' });
        subscriptions.forEach((s) => s.unsubscribe());
    });

    it('publishes saving and sync changes for the working submission without detaching its identity', () => {
        const submission = new TextSubmission();
        const other = new TextSubmission();
        expect(service.isSubmissionSaving(undefined)).toBe(false);
        service.setSubmissionSaving(submission, true);
        expect(service.isSubmissionSaving(submission)).toBe(true);
        expect(service.isSubmissionSaving(other)).toBe(false);
        service.setSubmissionSaving(submission, false);
        expect(service.isSubmissionSaving(submission)).toBe(false);
        const version = service.submissionSyncVersion();
        service.notifySubmissionSyncStateChanged();
        expect(service.submissionSyncVersion()).toBe(version + 1);
    });

    it('preserves the exercise group in conduction payloads and converts the exam session dates', () => {
        service.loadStudentExamWithExercisesForConduction(1, 2, 3).subscribe((loaded) => {
            expect(loaded?.exercises?.[0].exerciseGroup).toMatchObject({ id: 8, title: 'Group', isMandatory: true });
            expect(loaded?.exercises?.[0].exerciseGroup?.exam).toBe(loaded?.exam);
            expect(loaded?.exam?.startDate?.toISOString()).toBe('2026-01-01T10:00:00.000Z');
        });
        httpMock.expectOne({ method: 'GET', url: 'api/exam/courses/1/exams/2/student-exams/3/conduction' }).flush({
            id: 3,
            exam: { id: 2, startDate: '2026-01-01T10:00:00Z' },
            examSessions: [{ sessionToken: 'session-token' }],
            exercises: [{ id: 7, type: 'text', exerciseGroup: { id: 8, title: 'Group', isMandatory: true } }],
        });
        expect(TestBed.inject(SessionStorageService).retrieve('ExamSessionToken')).toBe('session-token');
    });
});
