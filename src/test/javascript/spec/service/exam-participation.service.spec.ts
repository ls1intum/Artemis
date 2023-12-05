import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { Exam } from 'app/entities/exam.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../test.module';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Course } from 'app/entities/course.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Result } from 'app/entities/result.model';
import { getLatestSubmissionResult } from 'app/entities/submission.model';
import { StudentExamWithGradeDTO, StudentResult } from 'app/exam/exam-scores/exam-score-dtos.model';
import { GradeType } from 'app/entities/grading-scale.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';

describe('Exam Participation Service', () => {
    let service: ExamParticipationService;
    let httpMock: HttpTestingController;
    let exam: Exam;
    let studentExam: StudentExam;
    let quizSubmission: QuizSubmission;
    let localStorage: LocalStorageService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, ArtemisTestModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        service = TestBed.inject(ExamParticipationService);
        httpMock = TestBed.inject(HttpTestingController);
        localStorage = TestBed.inject(LocalStorageService);

        exam = new Exam();
        studentExam = new StudentExam();
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
            .loadStudentExamGradeInfoForSummary(1, 1)
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
        const participationResult = new Result();
        participationResult.participation = studentParticipation;
        studentParticipation.results = [participationResult];
        const submission = new TextSubmission();
        submission.results = [new Result()];
        submission.participation = studentParticipation;
        getLatestSubmissionResult(submission)!.participation = studentParticipation;
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
    it('should save StudentExam to localStorage', async () => {
        const sendToService = Object.assign({ exercises: [] }, studentExam);
        const expected = Object.assign({}, sendToService);
        service.saveStudentExamToLocalStorage(1, 1, sendToService);
        jest.spyOn(localStorage, 'store').mockImplementation(() => {
            expect(localStorage['artemis_student_exam_1_1']).toBe(expected);
        });
    });
    it('should load StudentExam from localStorage', async () => {
        studentExam.exercises = [];
        service.saveStudentExamToLocalStorage(1, 1, studentExam);

        const stored = Object.assign({}, studentExam);
        jest.spyOn(localStorage, 'retrieve').mockReturnValue(JSON.stringify(stored));

        service.loadStudentExamWithExercisesForConductionFromLocalStorage(1, 1).subscribe((localExam: StudentExam) => {
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

    describe('getExerciseButtonTooltip', () => {
        const course = new Course();
        it('should return synced if submission does not exist', () => {
            const exercise = new TextExercise(course, undefined);
            const toolTip = service.getExerciseButtonTooltip(exercise);
            expect(toolTip).toBe('synced');
        });
        it('should return notSynced if exerciseType is not programming and submission.isSynced is false', () => {
            const exercise = new TextExercise(course, undefined);
            const submission = new TextSubmission();
            submission.isSynced = false;
            const studentParticipation = new StudentParticipation();
            studentParticipation.submissions = [submission];
            exercise.studentParticipations = [studentParticipation];
            const toolTip = service.getExerciseButtonTooltip(exercise);
            expect(toolTip).toBe('notSynced');
        });
        it('should return submitted if exerciseType is programming and submission.submitted and submission.isSynced is true', () => {
            const exercise = new ProgrammingExercise(course, undefined);
            const submission = new ProgrammingSubmission();
            submission.submitted = true;
            submission.isSynced = true;
            const studentParticipation = new StudentParticipation();
            studentParticipation.submissions = [submission];
            exercise.studentParticipations = [studentParticipation];
            const toolTip = service.getExerciseButtonTooltip(exercise);
            expect(toolTip).toBe('submitted');
        });
        it('should return notSubmitted if exerciseType is programming and submission.submitted is true and submission.isSynced is false', () => {
            const exercise = new ProgrammingExercise(course, undefined);
            const submission = new ProgrammingSubmission();
            submission.submitted = false;
            submission.isSynced = true;
            const studentParticipation = new StudentParticipation();
            studentParticipation.submissions = [submission];
            exercise.studentParticipations = [studentParticipation];
            const toolTip = service.getExerciseButtonTooltip(exercise);
            expect(toolTip).toBe('notSubmitted');
        });
        it('should return notSavedOrSubmitted if exerciseType is programming and submission.submitted is false and submission.isSynced is false', () => {
            const exercise = new ProgrammingExercise(course, undefined);
            const submission = new ProgrammingSubmission();
            submission.submitted = false;
            submission.isSynced = false;
            const studentParticipation = new StudentParticipation();
            studentParticipation.submissions = [submission];
            exercise.studentParticipations = [studentParticipation];
            const toolTip = service.getExerciseButtonTooltip(exercise);
            expect(toolTip).toBe('notSavedOrSubmitted');
        });
    });

    it('should return examExerciseIds', () => {
        const exerciseIds = [1, 2, 3];
        service.setExamExerciseIds(exerciseIds);
        expect(service.getExamExerciseIds()).toEqual(exerciseIds);
    });

    it('should set last save failed', () => {
        const storeSpy = jest.spyOn(localStorage, 'store');
        service.setLastSaveFailed(true, 1, 1);
        expect(storeSpy).toHaveBeenCalledWith('artemis_student_exam_1_1-save-failed', true);
    });

    it('should get last save failed', () => {
        const storeSpy = jest.spyOn(localStorage, 'retrieve');
        service.lastSaveFailed(1, 1);
        expect(storeSpy).toHaveBeenCalledWith('artemis_student_exam_1_1-save-failed');
    });
});
