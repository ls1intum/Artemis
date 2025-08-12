import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { take } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentExamWithGradeDTO, StudentResult } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { HttpErrorResponse, HttpHeaders, provideHttpClient } from '@angular/common/http';

describe('ExamParticipationService', () => {
    let service: ExamParticipationService;
    let httpMock: HttpTestingController;
    let exam: Exam;
    let studentExam: StudentExam;
    let quizSubmission: QuizSubmission;
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
        quizSubmission = new QuizSubmission();
    });

    it('should load a StudentExam with exercises for conduction', async () => {
        const returnedFromService = Object.assign({ started: true }, studentExam);
        service
            .loadStudentExamWithExercisesForConduction(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: studentExam }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('should load a StudentExam with exercises for conduction of TestExam', async () => {
        const returnedFromService = Object.assign({ started: true }, studentExam);
        service
            .loadStudentExamWithExercisesForConduction(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: studentExam }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('should load a StudentExam with exercises for summary', async () => {
        const returnedFromService = Object.assign({}, studentExam);
        service
            .loadStudentExamWithExercisesForSummary(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: studentExam }));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
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
            .subscribe((resp) => expect(resp).toMatchObject({ body: studentExamWithGrade }));
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
            .subscribe((resp) => expect(resp).toMatchObject({ body: studentExam }));

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
            .subscribe((resp) => expect(resp).toMatchObject({ body: studentExam }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
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
            .subscribe((resp) => expect(resp).toMatchObject({ body: studentExam }));

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
        service
            .submitStudentExam(2, 2, returnedFromService)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: returnedFromService }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
    });

    it('should update a QuizSubmission', async () => {
        const returnedFromService = Object.assign({}, quizSubmission);
        const expected = Object.assign({}, returnedFromService);
        service
            .updateQuizSubmission(1, expected)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
    });

    it('should load testRun with exercises for conduction', async () => {
        const returnedFromService = Object.assign({}, studentExam);
        const expected = Object.assign({}, returnedFromService);
        service
            .loadTestRunWithExercisesForConduction(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('save examSessionToken to sessionStorage', async () => {
        service.saveExamSessionTokenToSessionStorage('token1');
        jest.spyOn(sessionStorage, 'setItem').mockImplementation(() => {
            expect(sessionStorage['ExamSessionToken']).toBe('token1');
        });
    });

    it('should save and load StudentExam from localStorage', async () => {
        studentExam.exercises = [];
        service.saveStudentExamToLocalStorage(1, 1, studentExam);

        service.loadStudentExamWithExercisesForConductionFromLocalStorage(1, 1).subscribe((localExam: StudentExam) => {
            expect(localExam).toBeDefined();
            expect(localExam).toEqual(studentExam);
        });
    });

    it('should load a List of StudentExams for a user and course', async () => {
        const returnedFromService = Object.assign({}, [studentExam]);
        service
            .loadStudentExamsForTestExamsPerCourseAndPerUserForOverviewPage(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: [studentExam] }));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('should submit a StudentExam successfully', async () => {
        const studentExamCopy = Object.assign({}, studentExam);
        service
            .submitStudentExam(1, 1, studentExamCopy)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toBeUndefined());

        const req = httpMock.expectOne({ method: 'POST' });
        expect(req.request.url).toBe('api/exam/courses/1/exams/1/student-exams/submit');
        req.flush(null);
    });
    it('should fetch sidebar data successfully', async () => {
        const returnedFromService = [studentExam];
        service.getRealExamSidebarData(1).subscribe((resp) => expect(resp).toMatchObject({ body: returnedFromService }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    it('should throw error if submission is not in time', async () => {
        const errorHeaders = new HttpHeaders({ 'x-null-error': 'submissionNotInTime' });
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
});
