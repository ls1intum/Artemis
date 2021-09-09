import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import * as moment from 'moment';
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

describe('Exam Participation Service', () => {
    let injector: TestBed;
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
        injector = getTestBed();
        service = injector.get(ExamParticipationService);
        httpMock = injector.get(HttpTestingController);
        localStorage = injector.get(LocalStorageService);

        exam = new Exam();
        studentExam = new StudentExam();
        quizSubmission = new QuizSubmission();
    });

    it('should load a StudentExam with exercises for conduction', async () => {
        const returnedFromService = Object.assign({ started: true }, studentExam);
        service
            .loadStudentExamWithExercisesForConduction(1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: studentExam }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });
    it('should load a StudentExam with exercises for summary', async () => {
        const returnedFromService = Object.assign({}, studentExam);
        service
            .loadStudentExamWithExercisesForSummary(1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: studentExam }));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });
    it('should load a StudentExam', async () => {
        const sendExam = Object.assign(
            {
                visibleDate: moment(),
                startDate: moment(),
                endDate: moment(),
                publishResultDate: moment(),
                examStudentReviewStart: moment(),
                examStudentReviewEnd: moment(),
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
            .loadStudentExam(1, 1)
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

        const returnedFromService = Object.assign(
            {
                exercises: [exercise],
                exam: examToSend,
            },
            studentExam,
        );
        const expected = Object.assign({}, returnedFromService);
        service
            .submitStudentExam(2, 2, returnedFromService)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

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
        spyOn(sessionStorage, 'setItem').and.callFake(() => {
            expect(sessionStorage['ExamSessionToken']).toBe('token1');
        });
    });
    it('should save StudentExam to localStorage', async () => {
        const sendToService = Object.assign({ exercises: [] }, studentExam);
        const expected = Object.assign({}, sendToService);
        service.saveStudentExamToLocalStorage(1, 1, sendToService);
        spyOn(localStorage, 'store').and.callFake(() => {
            expect(localStorage['artemis_student_exam_1_1'].toBe(expected));
        });
    });
    it('should load StudentExam from localStorage', async () => {
        studentExam.exercises = [];
        service.saveStudentExamToLocalStorage(1, 1, studentExam);

        const stored = Object.assign({}, studentExam);
        spyOn(localStorage, 'retrieve').and.returnValues(JSON.stringify(stored));

        service.loadStudentExamWithExercisesForConductionFromLocalStorage(1, 1).subscribe((localExam: StudentExam) => {
            expect(localExam).toEqual(studentExam);
        });
    });
});
