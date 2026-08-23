import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/course/shared/entities/course.model';
import { QuizQuestionStatisticResponse } from 'app/quiz/manage/statistics/quiz-statistics-response.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { Subject, of } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MultipleChoiceQuestionStatisticComponent } from 'app/quiz/manage/statistics/multiple-choice-question-statistic/multiple-choice-question-statistic.component';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { MultipleChoiceQuestionStatistic } from 'app/quiz/shared/entities/multiple-choice-question-statistic.model';
import { AnswerCounter } from 'app/quiz/shared/entities/answer-counter.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { MockProvider } from 'ng-mocks';
import { ChangeDetectorRef } from '@angular/core';
import { QuizQuestionStatistic } from 'app/quiz/shared/entities/quiz-question-statistic.model';
import { greenColor, greyColor, redColor } from 'app/quiz/manage/statistics/question-statistic.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

const route = { params: of({ courseId: 3, exerciseId: 22, questionId: 1 }) };
const answerOption1 = { id: 5 } as AnswerOption;
const answerCounter = { answerId: answerOption1.id } as AnswerCounter;
const questionStatistic = { answerCounters: [answerCounter] } as MultipleChoiceQuestionStatistic;
const question = { id: 1, answerOptions: [answerOption1] } as MultipleChoiceQuestion;
const course = { id: 3 } as Course;
let quizExercise = { id: 22, quizStarted: true, course, quizQuestions: [question], questionId: 1, quizQuestionStatistic: questionStatistic } as QuizQuestionStatisticResponse;

describe('QuizExercise Multiple Choice Question Statistic Component', () => {
    let comp: MultipleChoiceQuestionStatisticComponent;
    let fixture: ComponentFixture<MultipleChoiceQuestionStatisticComponent>;
    let quizService: QuizExerciseService;
    let accountService: AccountService;
    let websocketService: MockWebsocketService;
    let router: Router;
    let accountSpy: any;
    let quizServiceFindSpy: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                MockProvider(ChangeDetectorRef),
                MockProvider(Router),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(MultipleChoiceQuestionStatisticComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MultipleChoiceQuestionStatisticComponent);
                comp = fixture.componentInstance;
                quizService = TestBed.inject(QuizExerciseService);
                accountService = TestBed.inject(AccountService);
                websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
                quizServiceFindSpy = vi.spyOn(quizService, 'findQuestionStatistic').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
                router = TestBed.inject(Router);
            });
    });

    afterEach(() => {
        quizExercise = { id: 22, quizStarted: true, course, quizQuestions: [question], questionId: 1, quizQuestionStatistic: questionStatistic } as QuizQuestionStatisticResponse;
    });

    describe('onInit', () => {
        it('should call functions on Init', () => {
            accountSpy = vi.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const loadQuizSpy = vi.spyOn(comp, 'loadQuiz');
            comp.websocketChannelForData = '';

            comp.ngOnInit();

            expect(accountSpy).toHaveBeenCalledTimes(2);
            expect(quizServiceFindSpy).toHaveBeenCalledWith(22, 1);
            expect(loadQuizSpy).toHaveBeenCalledWith(quizExercise, false);
            expect(comp.websocketChannelForData).toBe('/topic/statistic/22');
        });

        it('should not load Quiz if not authorised', () => {
            accountSpy = vi.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(false);
            const loadQuizSpy = vi.spyOn(comp, 'loadQuiz');

            comp.ngOnInit();

            expect(accountSpy).toHaveBeenCalledOnce();
            expect(quizServiceFindSpy).not.toHaveBeenCalled();
            expect(loadQuizSpy).not.toHaveBeenCalled();
        });

        it('should ignore an older request after a websocket refresh completes', () => {
            accountSpy = vi.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const initialResponse = new Subject<HttpResponse<QuizQuestionStatisticResponse>>();
            const refreshedResponse = new Subject<HttpResponse<QuizQuestionStatisticResponse>>();
            const refreshedStatistic = { answerCounters: [answerCounter] } as MultipleChoiceQuestionStatistic;
            const refreshedQuiz = { ...quizExercise, quizQuestionStatistic: refreshedStatistic };
            quizServiceFindSpy.mockReset().mockReturnValueOnce(initialResponse).mockReturnValueOnce(refreshedResponse);
            const loadQuizSpy = vi.spyOn(comp, 'loadQuiz');

            comp.ngOnInit();
            websocketService.emit('/topic/statistic/22', 22);
            refreshedResponse.next(new HttpResponse({ body: refreshedQuiz }));
            initialResponse.next(new HttpResponse({ body: quizExercise }));

            expect(loadQuizSpy).toHaveBeenCalledOnce();
            expect(loadQuizSpy).toHaveBeenCalledWith(refreshedQuiz, true);
            expect(comp.questionStatistic).toBe(refreshedStatistic);
        });
    });

    describe('loadLayout', () => {
        it('should call functions from loadLayout', () => {
            accountSpy = vi.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const resetLabelsSpy = vi.spyOn(comp, 'resetLabelsColors');
            const addLastBarSpy = vi.spyOn(comp, 'addLastBarLayout');
            const loadInvalidLayoutSpy = vi.spyOn(comp, 'loadInvalidLayout');
            const loadSolutionSpy = vi.spyOn(comp, 'loadSolutionLayout');

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
            accountSpy = vi.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const resetDataSpy = vi.spyOn(comp, 'resetData');
            const addDataSpy = vi.spyOn(comp, 'addData');
            const updateDataSpy = vi.spyOn(comp, 'updateData');

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

            expect(comp['chartColors']()).toEqual(backgroundColors);
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
            accountSpy = vi.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const navigateByUrlMock = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
            const emptyQuizExercise: QuizQuestionStatisticResponse = { ...quizExercise, quizQuestions: [] };

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

    describe('tooltip labels', () => {
        function labelFor(item: { dataIndex: number; parsed: { y: number } }): string {
            return (comp.chartOptions().plugins as any).tooltip.callbacks.label(item);
        }

        it('uses the participant-share tooltip for answer-option bars, including the last one while the solution is hidden', () => {
            comp.showSolution = false;
            // four answer-option counters, no appended correct-solutions bar in the default view
            comp.data = [2, 1, 1, 1];

            expect(labelFor({ dataIndex: 0, parsed: { y: 2 } })).toContain('tooltip.participantShare');
            expect(labelFor({ dataIndex: 3, parsed: { y: 1 } })).toContain('tooltip.participantShare');
            expect(labelFor({ dataIndex: 3, parsed: { y: 1 } })).not.toContain('tooltip.correctOverall');
        });

        it('uses the correct-overall tooltip only for the appended summary bar in the solution view', () => {
            comp.showSolution = true;
            // four answer options + the appended "correct solutions" summary bar
            comp.data = [2, 1, 1, 1, 3];

            expect(labelFor({ dataIndex: 4, parsed: { y: 3 } })).toContain('tooltip.correctOverall');
            expect(labelFor({ dataIndex: 0, parsed: { y: 2 } })).toContain('tooltip.participantShare');
        });
    });
});
