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
        const store = {};

        spyOn(localStorage, 'getItem').and.callFake(
            (key: string): String => {
                return store[key] || null;
            },
        );
        spyOn(localStorage, 'setItem').and.callFake((key: string, value: string): string => {
            return (store[key] = <string>value);
        });
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
            const returnedFromService = Object.assign({}, studentExam);
            service
                .loadStudentExam(1, 1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: studentExam }));

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(JSON.stringify(returnedFromService));
        });
        it('should load a StudentExam in the version of server', async () => {
            const returnedFromService = Object.assign({}, studentExam);
            const mockStudentExam = new StudentExam();
            mockStudentExam.exercises = [];
            service
                .submitStudentExam(1, 1, mockStudentExam)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: studentExam }));

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
        it('should save StudentExam to local storage', () => {
            expect(localStorage.setItem('foo', 'bar')).toBe('bar');
        });
    });
});
