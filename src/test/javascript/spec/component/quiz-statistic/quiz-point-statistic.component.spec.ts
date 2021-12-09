import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { ComponentFixture, discardPeriodicTasks, fakeAsync, TestBed, tick } from '@angular/core/testing';
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
import { QuizPointStatisticComponent } from 'app/exercises/quiz/manage/statistics/quiz-point-statistic/quiz-point-statistic.component';
import dayjs from 'dayjs';
import { QuizPointStatistic } from 'app/entities/quiz/quiz-point-statistic.model';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { MockProvider } from 'ng-mocks';
import { ChangeDetectorRef } from '@angular/core';

const route = { params: of({ courseId: 2, exerciseId: 42 }) };
const question = { id: 1 } as QuizQuestion;
const course = { id: 2 } as Course;
const pointCounters = [
    { points: 1, ratedCounter: 2, unRatedCounter: 3 },
    { points: 4, ratedCounter: 5, unRatedCounter: 6 },
];
let quizExercise = {
    id: 42,
    started: true,
    course,
    quizQuestions: [question],
    adjustedDueDate: undefined,
} as QuizExercise;

describe('QuizExercise Point Statistic Component', () => {
    let comp: QuizPointStatisticComponent;
    let fixture: ComponentFixture<QuizPointStatisticComponent>;
    let quizService: QuizExerciseService;
    let accountService: AccountService;
    let accountSpy: jest.SpyInstance;
    let router: Router;
    let translateService: TranslateService;
    let quizServiceFindSpy: jest.SpyInstance;
    Date.now = jest.fn(() => new Date(Date.UTC(2017, 0, 1)).valueOf());

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [QuizPointStatisticComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(ChangeDetectorRef),
            ],
        })
            .overrideTemplate(QuizPointStatisticComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizPointStatisticComponent);
                comp = fixture.componentInstance;
                quizService = fixture.debugElement.injector.get(QuizExerciseService);
                accountService = fixture.debugElement.injector.get(AccountService);
                router = fixture.debugElement.injector.get(Router);
                translateService = fixture.debugElement.injector.get(TranslateService);
                quizServiceFindSpy = jest.spyOn(quizService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
            });
    });

    afterEach(() => {
        quizExercise = { id: 42, started: true, course, quizQuestions: [question], adjustedDueDate: undefined } as QuizExercise;
    });

    describe('OnInit', function () {
        it('should call functions on Init', fakeAsync(() => {
            // setup
            jest.useFakeTimers();
            accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const loadQuizSuccessSpy = jest.spyOn(comp, 'loadQuizSuccess');
            const updateDisplayedTimesSpy = jest.spyOn(comp, 'updateDisplayedTimes');
            comp.quizExerciseChannel = '';
            comp.waitingForQuizStart = true;
            comp.quizExercise = quizExercise;
            comp.quizExercise.quizPointStatistic = new QuizPointStatistic();
            comp.quizExercise.quizPointStatistic.pointCounters = pointCounters;

            // call
            comp.ngOnInit();
            tick(); // simulate async
            jest.advanceTimersByTime(UI_RELOAD_TIME + 1); // simulate setInterval time passing

            // check
            expect(accountSpy).toHaveBeenCalled();
            expect(quizServiceFindSpy).toHaveBeenCalledWith(42);
            expect(loadQuizSuccessSpy).toHaveBeenCalledWith(quizExercise);
            expect(comp.quizExerciseChannel).toEqual('/topic/courses/2/quizExercises');
            expect(updateDisplayedTimesSpy).toHaveBeenCalledTimes(1);
            discardPeriodicTasks();
        }));

        it('should not load QuizSuccess if not authorised', fakeAsync(() => {
            accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(false);
            const loadQuizSuccessSpy = jest.spyOn(comp, 'loadQuizSuccess');

            comp.ngOnInit();
            tick();

            expect(accountSpy).toHaveBeenCalled();
            expect(quizServiceFindSpy).not.toHaveBeenCalled();
            expect(loadQuizSuccessSpy).not.toHaveBeenCalled();
            discardPeriodicTasks();
        }));
    });

    describe('updateDisplayedTimes', function () {
        it('should update remaining time ', () => {
            // setup
            quizExercise.adjustedDueDate = dayjs();
            comp.quizExercise = quizExercise;

            // call
            comp.updateDisplayedTimes();

            // check
            expect(comp.remainingTimeSeconds).toEqual(-1);
            expect(comp.remainingTimeText).toEqual(translateService.instant('showStatistic.quizhasEnded'));
        });

        it('should show remaining time as zero if time unknown', () => {
            // setup
            comp.quizExercise = quizExercise;

            // call
            comp.updateDisplayedTimes();

            // check
            expect(comp.remainingTimeSeconds).toEqual(0);
            expect(comp.remainingTimeText).toBe('?');
        });
    });

    it('should return remaining Time', () => {
        // only minutes if time > 2min 30sec
        expect(comp.relativeTimeText(220)).toEqual('4 min');

        // minutes and seconds if time in minutes between 1 <= x < 2.5
        expect(comp.relativeTimeText(130)).toEqual('2 min 10 s');

        // only seconds if time < 1min
        expect(comp.relativeTimeText(50)).toEqual('50 s');
    });

    describe('loadQuizSuccess', function () {
        let loadDataSpy: jest.SpyInstance;
        let routerSpy: jest.SpyInstance;

        beforeEach(() => {
            loadDataSpy = jest.spyOn(comp, 'loadData');
            routerSpy = jest.spyOn(router, 'navigate');
        });

        afterEach(() => {
            loadDataSpy.mockClear();
            routerSpy.mockClear();
        });

        it('should call router if called by student', () => {
            // setup
            accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(false);
            quizExercise.quizPointStatistic = new QuizPointStatistic();
            quizExercise.quizPointStatistic.pointCounters = pointCounters;

            // call
            comp.loadQuizSuccess(quizExercise);

            // check
            expect(routerSpy).toHaveBeenCalledWith(['courses']);
        });

        it('should load the quiz', () => {
            // setup
            accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            quizExercise.quizPointStatistic = new QuizPointStatistic();
            quizExercise.quizPointStatistic.pointCounters = pointCounters;

            // call
            comp.loadQuizSuccess(quizExercise);

            // check
            expect(routerSpy).not.toHaveBeenCalled();
            expect(comp.quizExercise).toEqual(quizExercise);
            expect(comp.waitingForQuizStart).toBe(false);
            expect(loadDataSpy).toHaveBeenCalled();
        });
    });

    it('should calculate the MaxScore', () => {
        // setup
        quizExercise.quizQuestions = [{ points: 1 }, { points: 2 }];
        comp.quizExercise = quizExercise;

        // call
        const result = comp.calculateMaxScore();

        // check
        expect(result).toBe(3);
    });

    describe('loadData', function () {
        it('should set data', () => {
            // setup
            const loadDataInDiagramSpy = jest.spyOn(comp, 'loadDataInDiagram');
            comp.quizPointStatistic = new QuizPointStatistic();
            comp.quizPointStatistic.pointCounters = pointCounters;

            // call
            comp.loadData();

            // check
            expect(loadDataInDiagramSpy).toHaveBeenCalled();
            expect(comp.label).toEqual(['1', '4']);
            expect(comp.ratedData).toEqual([2, 5]);
            expect(comp.unratedData).toEqual([3, 6]);
        });
    });
});
