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
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { QuizStatisticComponent } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';

const question = { id: 1 } as QuizQuestion;
const course = { id: 2 } as Course;
let quizExercise = { id: 42, started: true, course, quizQuestions: [question] } as QuizExercise;

const route = { params: of({ courseId: 2, exerciseId: 42 }) };

const quizQuestionStatOne = { ratedCorrectCounter: 1, unRatedCorrectCounter: 3 };
const quizQuestionStatTwo = { ratedCorrectCounter: 2, unRatedCorrectCounter: 4 };

describe('QuizExercise Statistic Component', () => {
    let comp: QuizStatisticComponent;
    let fixture: ComponentFixture<QuizStatisticComponent>;
    let quizService: QuizExerciseService;
    let accountService: AccountService;
    let accountSpy: jasmine.Spy;
    let router: Router;
    let quizServiceFindSpy: jasmine.Spy;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [QuizStatisticComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .overrideTemplate(QuizStatisticComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizStatisticComponent);
                comp = fixture.componentInstance;
                quizService = fixture.debugElement.injector.get(QuizExerciseService);
                accountService = fixture.debugElement.injector.get(AccountService);
                router = fixture.debugElement.injector.get(Router);
                quizServiceFindSpy = spyOn(quizService, 'find').and.returnValue(of(new HttpResponse({ body: quizExercise })));
            });
    });

    afterEach(() => {
        comp.ngOnDestroy();
        quizExercise = { id: 42, started: true, course, quizQuestions: [question] } as QuizExercise;
        quizServiceFindSpy.calls.reset();
    });

    describe('OnInit', function () {
        let loadQuizSuccessSpy: jasmine.Spy;
        let loadDataSpy: jasmine.Spy;

        beforeEach(() => {
            loadQuizSuccessSpy = loadQuizSuccessSpy = spyOn(comp, 'loadQuizSuccess');
            loadDataSpy = spyOn(comp, 'loadData');
        });

        afterEach(() => {
            loadQuizSuccessSpy.calls.reset();
            loadDataSpy.calls.reset();
        });

        it('should call functions on Init', fakeAsync(() => {
            // setup
            accountSpy = spyOn(accountService, 'hasAnyAuthorityDirect').and.returnValue(true);

            // call
            comp.ngOnInit();
            tick(); // simulate async

            // check
            expect(accountSpy).toHaveBeenCalled();
            expect(quizServiceFindSpy).toHaveBeenCalledWith(42);
            expect(loadQuizSuccessSpy).toHaveBeenCalledWith(quizExercise);
        }));

        it('should not load QuizSuccess if not authorised', fakeAsync(() => {
            // setup
            accountSpy = spyOn(accountService, 'hasAnyAuthorityDirect').and.returnValue(false);

            // call
            comp.ngOnInit();
            tick(); // simulate async

            // check
            expect(accountSpy).toHaveBeenCalled();
            expect(quizServiceFindSpy).not.toHaveBeenCalled();
            expect(loadQuizSuccessSpy).not.toHaveBeenCalled();
        }));
    });

    describe('loadQuizSuccess', function () {
        let calculateMaxScoreSpy: jasmine.Spy;
        let loadDataSpy: jasmine.Spy;

        beforeEach(() => {
            calculateMaxScoreSpy = spyOn(comp, 'calculateMaxScore').and.returnValue(32);
            loadDataSpy = spyOn(comp, 'loadData');
        });

        afterEach(() => {
            calculateMaxScoreSpy.calls.reset();
            loadDataSpy.calls.reset();
        });

        it('should set data', () => {
            // setup
            accountSpy = spyOn(accountService, 'hasAnyAuthorityDirect').and.returnValue(true);

            // call
            comp.loadQuizSuccess(quizExercise);

            // check
            expect(comp.quizExercise).toBe(quizExercise);
            expect(calculateMaxScoreSpy).toHaveBeenCalled();
            expect(comp.maxScore).toEqual(32);
            expect(loadDataSpy).toHaveBeenCalled();
        });

        it('should call navigate to courses if called by student', () => {
            // setup
            accountSpy = spyOn(accountService, 'hasAnyAuthorityDirect').and.returnValue(false);
            const routerSpy = spyOn(router, 'navigate');

            // call
            comp.loadQuizSuccess(quizExercise);

            // check
            expect(routerSpy).toHaveBeenCalledWith(['/courses']);
        });
    });

    describe('calculateMaxScore', function () {
        it('should return MaxScore by looping over scores', () => {
            // setup
            quizExercise.quizQuestions = [{ score: 1 }, { score: 2 }];
            comp.quizExercise = quizExercise;

            // call
            const result = comp.calculateMaxScore();

            // check
            expect(result).toBe(3);
        });

        it('should return MaxScore be using quizExercise.maxScore', () => {
            // setup
            quizExercise.quizQuestions = undefined;
            quizExercise.maxScore = 42;
            comp.quizExercise = quizExercise;

            // call
            const result = comp.calculateMaxScore();

            // check
            expect(result).toBe(42);
        });
    });

    describe('loadData', function () {
        beforeEach(() => {
            quizExercise.quizQuestions = [
                { quizQuestionStatistic: quizQuestionStatOne, score: 5 },
                { quizQuestionStatistic: quizQuestionStatTwo, score: 6 },
            ];
            quizExercise.quizPointStatistic = { participantsRated: 42 };
            comp.quizExercise = quizExercise;
        });

        it('should use values of quizExercise and rated data', () => {
            // setup
            const updateChartSpy = spyOn(comp, 'updateChart');
            comp.rated = true;
            comp.maxScore = 1;

            // call
            comp.loadData();

            // check
            expect(updateChartSpy).toHaveBeenCalled();
            expect(comp.ratedData).toEqual([1, 2, 17]);
            expect(comp.unratedData).toEqual([3, 4, 39]);
            expect(comp.data).toEqual([1, 2, 17]);
            expect(comp.participants).toBe(42);
        });

        it('should use values of quizExercise and unrated data', () => {
            // setup
            comp.rated = false;
            comp.maxScore = 1;

            // call
            comp.loadData();

            // check
            expect(comp.unratedData).toEqual([3, 4, 39]);
            expect(comp.data).toEqual([3, 4, 39]);
        });

        it('should use defaults if no quizQuestions are not set', () => {
            // setup
            const updateChartSpy = spyOn(comp, 'updateChart');
            quizExercise.quizQuestions = [];
            comp.rated = true;
            comp.maxScore = 1;
            comp.quizExercise = quizExercise;

            // call
            comp.loadData();

            // check
            expect(updateChartSpy).toHaveBeenCalled();
            expect(comp.ratedData).toEqual([0]);
            expect(comp.unratedData).toEqual([0]);
            expect(comp.data).toEqual([0]);
            expect(comp.participants).toBe(42);
        });
    });
});
