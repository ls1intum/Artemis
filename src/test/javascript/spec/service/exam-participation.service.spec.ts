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
        it('loadStudentExamWithExercisesForConduction', async () => {
            const returnedFromService = Object.assign({ started: true }, studentExam);
            service
                .loadStudentExamWithExercisesForConduction(1, 1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: studentExam }));

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(JSON.stringify(returnedFromService));
        });
    });
});
