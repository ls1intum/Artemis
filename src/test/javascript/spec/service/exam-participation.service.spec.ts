import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { map, take } from 'rxjs/operators';
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
import { expect } from '../helpers/jasmine.jest.fix';

describe('Exam Participation Service', () => {
    let injector: TestBed;
    let service: ExamParticipationService;
    let httpMock: HttpTestingController;
    let exam: Exam;
    let studentExam: StudentExam;
    let quizSubmission: QuizSubmission;
    let currentDate: moment.Moment;
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
        currentDate = moment();

        exam = new Exam();
        studentExam = new StudentExam();
        quizSubmission = new QuizSubmission();
    });

    describe('Service methods', async () => {
        it('should load a StudentExam with exercises for conduction', async () => {
            const returnedFromService = Object.assign({ started: true }, studentExam);
            service
                .loadStudentExamWithExercisesForConduction(1, 1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: studentExam }));

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(JSON.stringify(returnedFromService));
        });
        it('should load a StudentExam with exercises for summary', async () => {
            const returnedFromService = Object.assign({}, studentExam);
            service
                .loadStudentExamWithExercisesForSummary(1, 1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: studentExam }));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(JSON.stringify(returnedFromService));
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
            req.flush(JSON.stringify(returnedFromService));
        });
        it('should load a StudentExam in the version of server', async () => {
            const returnedFromService = Object.assign(
                {
                    exercises: [],
                },
                studentExam,
            );
            const expected = Object.assign({}, returnedFromService);
            service
                .submitStudentExam(1, 1, returnedFromService)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(JSON.stringify(returnedFromService));
        });
        it('should update a QuizSubmission', async () => {
            const returnedFromService = Object.assign({}, quizSubmission);
            const expected = Object.assign({}, returnedFromService);
            service
                .updateQuizSubmission(1, expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(JSON.stringify(returnedFromService));
        });
        it('should load testRun with exercises for conduction', async () => {
            const returnedFromService = Object.assign({}, studentExam);
            const expected = Object.assign({}, returnedFromService);
            service
                .loadTestRunWithExercisesForConduction(1, 1, 1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(JSON.stringify(returnedFromService));
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
            spyOn(localStorage, 'setItem').and.callFake(() => {
                expect(localStorage['artemis_student_exam_1_1'].toBe(JSON.stringify(expected)));
            });
        });
    });
});
