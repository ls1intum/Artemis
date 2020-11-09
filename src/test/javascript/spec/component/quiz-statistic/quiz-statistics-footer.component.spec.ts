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
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { QuizStatisticUtil } from 'app/exercises/quiz/shared/quiz-statistic-util.service';

const question = { id: 1 } as QuizQuestion;
const course = { id: 2 } as Course;
let quizExercise = { id: 42, started: true, course, quizQuestions: [question] } as QuizExercise;
const route = { params: of({ questionId: 1, exerciseId: 42 }) };

describe('QuizExercise Statistic Footer Component', () => {
    let comp: QuizStatisticsFooterComponent;
    let fixture: ComponentFixture<QuizStatisticsFooterComponent>;
    let quizService: QuizExerciseService;
    let accountService: AccountService;
    let accountSpy: jasmine.Spy;
    let routerSpy: jasmine.Spy;
    let router: Router;
    let quizStatisticUtil: QuizStatisticUtil;
    let quizServiceFindSpy: jasmine.Spy;

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
                quizStatisticUtil = fixture.debugElement.injector.get(QuizStatisticUtil);
                routerSpy = spyOn(router, 'navigateByUrl');
                accountSpy = spyOn(accountService, 'hasAnyAuthorityDirect').and.returnValue(true);
                quizServiceFindSpy = spyOn(quizService, 'find').and.returnValue(of(new HttpResponse({ body: quizExercise })));
            });
    });

    afterEach(() => {
        comp.ngOnDestroy();
        quizExercise = { id: 42, started: true, course, quizQuestions: [question] } as QuizExercise;
    });

    it('Should load Quiz on Init', fakeAsync(() => {
        // setup
        jest.useFakeTimers();
        const loadSpy = spyOn(comp, 'loadQuiz').and.callThrough();
        const updateDisplayedTimesSpy = spyOn(comp, 'updateDisplayedTimes');

        // call
        comp.ngOnInit();
        tick(); // simulate async
        jest.advanceTimersByTime(201); // simulate setInterval time passing

        // check
        expect(accountSpy).toHaveBeenCalled();
        expect(quizServiceFindSpy).toHaveBeenCalledWith(42);
        expect(loadSpy).toHaveBeenCalledWith(quizExercise);
        expect(comp.question).toEqual(question);
        expect(updateDisplayedTimesSpy).toHaveBeenCalledTimes(1);
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
        expect(comp.waitingForQuizStart).toEqual(false);
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
            const quizStatisticUtilSpy = spyOn(quizStatisticUtil, 'navigateToStatisticOf');
            comp.quizExercise = quizExercise;
            comp.isQuizStatistic = false;
            comp.isQuizPointStatistic = true;

            // call
            comp.previousStatistic();

            // check
            expect(quizStatisticUtilSpy).toHaveBeenCalledWith(quizExercise, question);
        });

        it('should call util previous Statistic ', () => {
            // setup
            const quizStatisticUtilSpy = spyOn(quizStatisticUtil, 'previousStatistic');
            comp.quizExercise = quizExercise;
            comp.question = question;
            comp.isQuizStatistic = false;
            comp.isQuizPointStatistic = false;

            // call
            // comp.previousStatistic();

            // check
            expect(quizStatisticUtilSpy).not.toHaveBeenCalledWith(quizExercise, question);
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

        it('should go to quiz-statistic', () => {
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
            const quizStatisticUtilSpy = spyOn(quizStatisticUtil, 'navigateToStatisticOf');
            comp.quizExercise = quizExercise;
            comp.isQuizPointStatistic = false;
            comp.isQuizStatistic = true;

            // call
            comp.nextStatistic();

            // check
            expect(quizStatisticUtilSpy).toHaveBeenCalledWith(quizExercise, question);
        });

        it('should call util next Statistic ', () => {
            // setup
            const quizStatisticUtilSpy = spyOn(quizStatisticUtil, 'nextStatistic');
            comp.quizExercise = quizExercise;
            comp.question = question;
            comp.isQuizPointStatistic = false;
            comp.isQuizStatistic = false;

            // call
            comp.nextStatistic();

            // check
            expect(quizStatisticUtilSpy).toHaveBeenCalledWith(quizExercise, question);
        });
    });
});
