import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { ComponentFixture, TestBed, tick, fakeAsync } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizStatisticsFooterComponent } from 'app/exercises/quiz/manage/statistics/quiz-statistics-footer/quiz-statistics-footer.component';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';

const question = { id: 1, type: QuizQuestionType.MULTIPLE_CHOICE } as QuizQuestion;
const course = { id: 2 } as Course;
let quizExercise = { id: 42, quizStarted: true, course, quizQuestions: [question] } as QuizExercise;
let examQuizExercise = { id: 43, quizStarted: true, course, quizQuestions: [question], exerciseGroup: { id: 11, exam: { id: 10 } } } as QuizExercise;
const route = { params: of({ questionId: 1, exerciseId: 42 }) };

describe('QuizExercise Statistic Footer Component', () => {
    let comp: QuizStatisticsFooterComponent;
    let fixture: ComponentFixture<QuizStatisticsFooterComponent>;
    let quizService: QuizExerciseService;
    let accountService: AccountService;
    let accountSpy: jest.SpyInstance;
    let routerSpy: jest.SpyInstance;
    let router: Router;
    let quizServiceFindSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [QuizStatisticsFooterComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .overrideTemplate(QuizStatisticsFooterComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizStatisticsFooterComponent);
                comp = fixture.componentInstance;
                quizService = fixture.debugElement.injector.get(QuizExerciseService);
                accountService = fixture.debugElement.injector.get(AccountService);
                router = fixture.debugElement.injector.get(Router);
                routerSpy = jest.spyOn(router, 'navigateByUrl');
                accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
                quizServiceFindSpy = jest.spyOn(quizService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
            });
    });

    afterEach(() => {
        comp.ngOnDestroy();
        quizExercise = { id: 42, quizStarted: true, course, quizQuestions: [question] } as QuizExercise;
        examQuizExercise = { id: 43, quizStarted: true, course, quizQuestions: [question], exerciseGroup: { id: 11, exam: { id: 10 } } } as QuizExercise;
    });

    it('Should load Quiz on Init', fakeAsync(() => {
        // setup
        jest.useFakeTimers();
        const loadSpy = jest.spyOn(comp, 'loadQuiz');
        const updateDisplayedTimesSpy = jest.spyOn(comp, 'updateDisplayedTimes');

        // call
        comp.ngOnInit();
        tick(); // simulate async
        jest.advanceTimersByTime(UI_RELOAD_TIME + 1); // simulate setInterval time passing

        // check
        expect(accountSpy).toHaveBeenCalled();
        expect(quizServiceFindSpy).toHaveBeenCalledWith(42);
        expect(loadSpy).toHaveBeenCalledWith(quizExercise);
        expect(comp.question).toEqual(question);
        expect(updateDisplayedTimesSpy).toHaveBeenCalledOnce();
    }));

    it('should set quiz and update properties', () => {
        // setup
        comp.questionIdParam = 1;

        // call
        comp.loadQuiz(quizExercise);

        // check
        expect(accountSpy).toHaveBeenCalled();
        expect(comp.quizExercise).toEqual(quizExercise);
        expect(comp.question).toEqual(question);
        expect(comp.waitingForQuizStart).toBeFalse();
    });

    it('should return remaining Time', () => {
        // only minutes if time > 2min 30sec
        expect(comp.relativeTimeText(220)).toEqual('4 min');

        // minutes and seconds if time in minutes between 1 <= x < 2.5
        expect(comp.relativeTimeText(130)).toEqual('2 min 10 s');

        // only seconds if time < 1min
        expect(comp.relativeTimeText(50)).toEqual('50 s');
    });

    describe('test previous statistic', () => {
        // setup
        it('should go to quiz-point-statistic', () => {
            // setup
            comp.quizExercise = quizExercise;
            comp.isQuizStatistic = true;

            // call
            comp.previousStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/quiz-exercises/42/quiz-point-statistic`);
        });

        it('should go to quiz-statistic', () => {
            // setup
            quizExercise.quizQuestions = [];
            comp.quizExercise = quizExercise;
            comp.isQuizStatistic = false;
            comp.isQuizPointStatistic = true;

            // check
            comp.previousStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/quiz-exercises/42/quiz-statistic`);
        });

        it('should go to previous statistic', () => {
            // setup
            comp.quizExercise = quizExercise;
            comp.isQuizStatistic = false;
            comp.isQuizPointStatistic = true;

            // call
            comp.previousStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/quiz-exercises/42/mc-question-statistic/1`);
        });

        it('should call util previous Statistic', () => {
            // setup
            comp.quizExercise = quizExercise;
            comp.question = question;
            comp.isQuizStatistic = false;
            comp.isQuizPointStatistic = false;

            // call
            comp.previousStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/quiz-exercises/42/quiz-point-statistic`);
        });
    });

    describe('test previous statistic for exams', () => {
        // setup
        it('should go to quiz-point-statistic for exam', () => {
            // setup
            comp.quizExercise = examQuizExercise;
            comp.isQuizStatistic = true;

            // call
            comp.previousStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/exams/10/exercise-groups/11/quiz-exercises/43/quiz-point-statistic`);
        });

        it('should go to quiz-statistic for exam', () => {
            // setup
            examQuizExercise.quizQuestions = [];
            comp.quizExercise = examQuizExercise;
            comp.isQuizStatistic = false;
            comp.isQuizPointStatistic = true;

            // check
            comp.previousStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/exams/10/exercise-groups/11/quiz-exercises/43/quiz-statistic`);
        });

        it('should go to previous statistic for exam', () => {
            // setup
            comp.quizExercise = examQuizExercise;
            comp.isQuizStatistic = false;
            comp.isQuizPointStatistic = true;

            // call
            comp.previousStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/exams/10/exercise-groups/11/quiz-exercises/43/mc-question-statistic/1`);
        });

        it('should call util previous Statistic for exam', () => {
            // setup
            comp.quizExercise = examQuizExercise;
            comp.question = question;
            comp.isQuizStatistic = false;
            comp.isQuizPointStatistic = false;

            // call
            comp.previousStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/exams/10/exercise-groups/11/quiz-exercises/43/quiz-point-statistic`);
        });
    });

    describe('test next statistic', () => {
        it('should go to quiz-statistic', () => {
            // setup
            comp.quizExercise = quizExercise;
            comp.isQuizPointStatistic = true;

            // call
            comp.nextStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/quiz-exercises/42/quiz-statistic`);
        });

        it('should go to quiz-statistic with points', () => {
            // setup
            quizExercise.quizQuestions = [];
            comp.quizExercise = quizExercise;
            comp.isQuizPointStatistic = false;
            comp.isQuizStatistic = true;

            // call
            comp.nextStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/quiz-exercises/42/quiz-point-statistic`);
        });

        it('should go to next statistic', () => {
            // setup
            comp.quizExercise = quizExercise;
            comp.isQuizPointStatistic = false;
            comp.isQuizStatistic = true;

            // call
            comp.nextStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/quiz-exercises/42/mc-question-statistic/1`);
        });

        it('should call util next Statistic', () => {
            // setup
            comp.quizExercise = quizExercise;
            comp.question = question;
            comp.isQuizPointStatistic = false;
            comp.isQuizStatistic = false;

            // call
            comp.nextStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/quiz-exercises/42/quiz-point-statistic`);
        });
    });

    describe('test next statistic for exams', () => {
        it('should go to quiz-statistic for exam', () => {
            // setup
            comp.quizExercise = examQuizExercise;
            comp.isQuizPointStatistic = true;

            // call
            comp.nextStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/exams/10/exercise-groups/11/quiz-exercises/43/quiz-statistic`);
        });

        it('should go to quiz-statistic with points for exam', () => {
            // setup
            examQuizExercise.quizQuestions = [];
            comp.quizExercise = examQuizExercise;
            comp.isQuizPointStatistic = false;
            comp.isQuizStatistic = true;

            // call
            comp.nextStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/exams/10/exercise-groups/11/quiz-exercises/43/quiz-point-statistic`);
        });

        it('should go to next statistic for exam', () => {
            // setup
            comp.quizExercise = examQuizExercise;
            comp.isQuizPointStatistic = false;
            comp.isQuizStatistic = true;

            // call
            comp.nextStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/exams/10/exercise-groups/11/quiz-exercises/43/mc-question-statistic/1`);
        });

        it('should call util next Statistic for exam', () => {
            // setup
            comp.quizExercise = examQuizExercise;
            comp.question = question;
            comp.isQuizPointStatistic = false;
            comp.isQuizStatistic = false;

            // call
            comp.nextStatistic();

            // check
            expect(routerSpy).toHaveBeenCalledWith(`/course-management/2/exams/10/exercise-groups/11/quiz-exercises/43/quiz-point-statistic`);
        });
    });
});
