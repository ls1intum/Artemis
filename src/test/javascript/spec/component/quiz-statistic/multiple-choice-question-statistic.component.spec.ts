import { HttpResponse } from '@angular/common/http';
import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { AnswerCounter } from 'app/entities/quiz/answer-counter.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { MultipleChoiceQuestionStatistic } from 'app/entities/quiz/multiple-choice-question-statistic.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestionStatistic } from 'app/entities/quiz/quiz-question-statistic.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { MultipleChoiceQuestionStatisticComponent } from 'app/exercises/quiz/manage/statistics/multiple-choice-question-statistic/multiple-choice-question-statistic.component';
import { greenColor, greyColor, redColor } from 'app/exercises/quiz/manage/statistics/question-statistic.component';
import { MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

const route = { params: of({ courseId: 3, exerciseId: 22, questionId: 1 }) };
const answerOption1 = { id: 5 } as AnswerOption;
const answerCounter = { answer: answerOption1 } as AnswerCounter;
const questionStatistic = { answerCounters: [answerCounter] } as MultipleChoiceQuestionStatistic;
const question = { id: 1, answerOptions: [answerOption1], quizQuestionStatistic: questionStatistic } as MultipleChoiceQuestion;
const course = { id: 3 } as Course;
let quizExercise = { id: 22, quizStarted: true, course, quizQuestions: [question] } as QuizExercise;

describe('QuizExercise Multiple Choice Question Statistic Component', () => {
    let comp: MultipleChoiceQuestionStatisticComponent;
    let fixture: ComponentFixture<MultipleChoiceQuestionStatisticComponent>;
    let quizService: QuizExerciseService;
    let accountService: AccountService;
    let router: Router;
    let accountSpy: jest.SpyInstance;
    let quizServiceFindSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [MultipleChoiceQuestionStatisticComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(ChangeDetectorRef),
                MockProvider(Router),
            ],
        })
            .overrideTemplate(MultipleChoiceQuestionStatisticComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MultipleChoiceQuestionStatisticComponent);
                comp = fixture.componentInstance;
                quizService = fixture.debugElement.injector.get(QuizExerciseService);
                accountService = fixture.debugElement.injector.get(AccountService);
                quizServiceFindSpy = jest.spyOn(quizService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
                router = TestBed.inject(Router);
            });
    });

    afterEach(() => {
        quizExercise = { id: 22, quizStarted: true, course, quizQuestions: [question] } as QuizExercise;
    });

    describe('onInit', () => {
        it('should call functions on Init', () => {
            accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const loadQuizSpy = jest.spyOn(comp, 'loadQuiz');
            comp.websocketChannelForData = '';

            comp.ngOnInit();

            expect(accountSpy).toHaveBeenCalledTimes(2);
            expect(quizServiceFindSpy).toHaveBeenCalledWith(22);
            expect(loadQuizSpy).toHaveBeenCalledWith(quizExercise, false);
            expect(comp.websocketChannelForData).toBe('/topic/statistic/22');
        });

        it('should not load Quiz if not authorised', () => {
            accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(false);
            const loadQuizSpy = jest.spyOn(comp, 'loadQuiz');

            comp.ngOnInit();

            expect(accountSpy).toHaveBeenCalledOnce();
            expect(quizServiceFindSpy).not.toHaveBeenCalled();
            expect(loadQuizSpy).not.toHaveBeenCalled();
        });
    });

    describe('loadLayout', () => {
        it('should call functions from loadLayout', () => {
            accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const resetLabelsSpy = jest.spyOn(comp, 'resetLabelsColors');
            const addLastBarSpy = jest.spyOn(comp, 'addLastBarLayout');
            const loadInvalidLayoutSpy = jest.spyOn(comp, 'loadInvalidLayout');
            const loadSolutionSpy = jest.spyOn(comp, 'loadSolutionLayout');

            comp.ngOnInit();
            comp.loadLayout();

            expect(resetLabelsSpy).toHaveBeenCalledTimes(2);
            expect(addLastBarSpy).toHaveBeenCalledTimes(2);
            expect(loadInvalidLayoutSpy).toHaveBeenCalledTimes(2);
            expect(loadSolutionSpy).toHaveBeenCalledTimes(2);
        });
    });

    describe('loadData', () => {
        let backgroundColors: string[];
        let labels: string[];
        beforeEach(() => {
            backgroundColors = ['#fcba03', '#035efc', '#fc03d2', '#fc5203'];
            labels = ['test', 'test2', 'test3', 'test4'];
        });
        it('should call functions from loadData', () => {
            accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const resetDataSpy = jest.spyOn(comp, 'resetData');
            const addDataSpy = jest.spyOn(comp, 'addData');
            const updateDataSpy = jest.spyOn(comp, 'updateData');

            comp.ngOnInit();
            comp.loadData();

            expect(resetDataSpy).toHaveBeenCalledTimes(2);
            expect(addDataSpy).toHaveBeenCalledTimes(2);
            expect(updateDataSpy).toHaveBeenCalledTimes(2);
        });

        it('should load solution data in diagram', () => {
            comp.showSolution = true;
            comp.backgroundSolutionColors = backgroundColors;
            comp.questionStatistic = new QuizQuestionStatistic();
            comp.rated = true;
            comp.ratedCorrectData = 42;
            comp.solutionLabels = labels;

            comp.loadDataInDiagram();

            expect(comp.ngxColor.domain).toEqual(backgroundColors);
            expect(comp.data).toEqual([42]);
            expect(comp.chartLabels).toEqual(labels);
        });

        it('should mark option as invalid', () => {
            const elements = [{ invalid: false }, { invalid: true }, { invalid: false }, { invalid: true }];
            comp.backgroundColors = backgroundColors;
            comp.backgroundSolutionColors = backgroundColors;
            comp.labels = labels;

            comp.loadInvalidLayout(elements);

            expect(comp.backgroundColors).toEqual(['#fcba03', greyColor, '#fc03d2', greyColor]);
            expect(comp.backgroundSolutionColors).toEqual(['#fcba03', greyColor, '#fc03d2', greyColor]);
            expect(comp.labels).toEqual(['test', 'B. artemisApp.showStatistic.invalid', 'test3', 'D. artemisApp.showStatistic.invalid']);
        });

        it('should navigate back if the quiz does not contain any questions', () => {
            accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const navigateByUrlMock = jest.spyOn(router, 'navigateByUrl').mockImplementation();
            const emptyQuizExercise = new QuizExercise(undefined, undefined);

            const result = comp.loadQuizCommon(emptyQuizExercise);

            expect(navigateByUrlMock).toHaveBeenCalledOnce();
            expect(navigateByUrlMock).toHaveBeenCalledWith('courses');
            expect(result).toBeUndefined();
        });

        it('should load the layout for the solution', () => {
            const mcQuestion = new MultipleChoiceQuestion();
            mcQuestion.answerOptions = [
                { isCorrect: true, invalid: false },
                { isCorrect: false, invalid: false },
                { isCorrect: true, invalid: true },
                { isCorrect: false, invalid: true },
            ];
            comp.question = mcQuestion;
            comp.backgroundSolutionColors = backgroundColors;
            comp.solutionLabels = labels;

            comp.loadSolutionLayout();

            expect(comp.backgroundSolutionColors).toEqual([greenColor, redColor, '#fc03d2', '#fc5203']);
            expect(comp.solutionLabels).toEqual([
                'A. (artemisApp.showStatistic.questionStatistic.correct)',
                'B. (artemisApp.showStatistic.questionStatistic.incorrect)',
                'test3',
                'test4',
            ]);
        });
    });
});
