import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/course/shared/entities/course.model';
import { QuizExercise, QuizExerciseStatisticUpdate } from 'app/quiz/shared/entities/quiz-exercise.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ShortAnswerQuestionStatisticComponent } from 'app/quiz/manage/statistics/short-answer-question-statistic/short-answer-question-statistic.component';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';
import { ShortAnswerQuestionStatistic } from 'app/quiz/shared/entities/short-answer-question-statistic.model';
import { ShortAnswerSpotCounter } from 'app/quiz/shared/entities/short-answer-spot-counter.model';
import { ShortAnswerMapping } from 'app/quiz/shared/entities/short-answer-mapping.model';
import { ShortAnswerSolution } from 'app/quiz/shared/entities/short-answer-solution.model';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { MockProvider } from 'ng-mocks';
import { ChangeDetectorRef } from '@angular/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

const route = { params: of({ courseId: 1, exerciseId: 4, questionId: 1 }) };
const answerSpot = { posX: 5, invalid: false, id: 1, tempID: 2 } as ShortAnswerSpot;
const shortAnswerCounter = { spotId: answerSpot.id } as ShortAnswerSpotCounter;
const shortAnswerSolution = { id: 1, tempID: 2 } as ShortAnswerSolution;
const shortAnswerMapping = { spot: answerSpot, solution: shortAnswerSolution } as ShortAnswerMapping;
const questionStatistic = { shortAnswerSpotCounters: [shortAnswerCounter] } as ShortAnswerQuestionStatistic;
const question = {
    id: 1,
    spots: [answerSpot],
    text: 'Test Question',
    quizQuestionStatistic: questionStatistic,
    solutions: [shortAnswerSolution],
    correctMappings: [shortAnswerMapping],
} as ShortAnswerQuestion;
const course = { id: 1 } as Course;
let quizExercise = {
    id: 4,
    title: 'Quiz title',
    started: true,
    course,
    quizQuestions: [question],
    adjustedDueDate: undefined,
    numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()],
    studentAssignedTeamIdComputed: false,
    secondCorrectionEnabled: true,
} as QuizExercise;

describe('QuizExercise Short Answer Question Statistic Component', () => {
    let comp: ShortAnswerQuestionStatisticComponent;
    let fixture: ComponentFixture<ShortAnswerQuestionStatisticComponent>;
    let quizService: QuizExerciseService;
    let accountService: AccountService;
    let websocketService: MockWebsocketService;
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
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(ShortAnswerQuestionStatisticComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ShortAnswerQuestionStatisticComponent);
                comp = fixture.componentInstance;
                quizService = TestBed.inject(QuizExerciseService);
                accountService = TestBed.inject(AccountService);
                websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
                quizServiceFindSpy = vi.spyOn(quizService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
            });
    });

    afterEach(() => {
        quizExercise = {
            id: 4,
            title: 'Quiz title',
            started: true,
            course,
            quizQuestions: [question],
            adjustedDueDate: undefined,
            numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()],
            studentAssignedTeamIdComputed: false,
            secondCorrectionEnabled: true,
        } as QuizExercise;
    });

    describe('onInit', () => {
        it('should call functions on Init', () => {
            accountSpy = vi.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const loadQuizSpy = vi.spyOn(comp, 'loadQuiz');
            comp.websocketChannelForData = '';

            comp.ngOnInit();

            expect(accountSpy).toHaveBeenCalledTimes(2);
            expect(quizServiceFindSpy).toHaveBeenCalledWith(4);
            expect(loadQuizSpy).toHaveBeenCalledWith(quizExercise, false);
            expect(comp.websocketChannelForData).toBe('/topic/statistic/4');
        });

        it('should not load Quiz if not authorised', () => {
            accountSpy = vi.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(false);
            const loadQuizSpy = vi.spyOn(comp, 'loadQuiz');

            comp.ngOnInit();

            expect(accountSpy).toHaveBeenCalledOnce();
            expect(quizServiceFindSpy).not.toHaveBeenCalled();
            expect(loadQuizSpy).not.toHaveBeenCalled();
        });

        it('should merge solution-free statistic updates into the loaded quiz', () => {
            accountSpy = vi.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const updatedStatistic = { shortAnswerSpotCounters: [{ spot: answerSpot, ratedCounter: 2, unRatedCounter: 3 }] } as ShortAnswerQuestionStatistic;
            const update = {
                id: 4,
                quizQuestions: [{ id: 1, spots: [answerSpot], text: 'Test Question', quizQuestionStatistic: updatedStatistic } as ShortAnswerQuestion],
            } as QuizExerciseStatisticUpdate;

            comp.ngOnInit();
            websocketService.emit('/topic/statistic/4', update);

            expect(comp.quizExercise.title).toBe('Quiz title');
            expect(comp.question.quizQuestionStatistic).toBe(updatedStatistic);
            expect((comp.question as ShortAnswerQuestion).solutions).toEqual([shortAnswerSolution]);
            expect((comp.question as ShortAnswerQuestion).correctMappings).toEqual([shortAnswerMapping]);
            expect(comp.sampleSolutions).toEqual([shortAnswerSolution]);
        });
    });

    describe('loadQuiz', () => {
        it('should call functions from loadQuiz', () => {
            accountSpy = vi.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const generateStructureSpy = vi.spyOn(comp, 'generateShortAnswerStructure');
            const generateLettersSpy = vi.spyOn(comp, 'generateLettersForSolutions');
            const loadLayoutSpy = vi.spyOn(comp, 'loadLayout');
            const loadDataSpy = vi.spyOn(comp, 'loadData');

            comp.ngOnInit();
            comp.loadQuiz(quizExercise, false);

            expect(generateStructureSpy).toHaveBeenCalledTimes(2);
            expect(generateLettersSpy).toHaveBeenCalledTimes(2);
            expect(loadLayoutSpy).toHaveBeenCalledTimes(2);
            expect(loadDataSpy).toHaveBeenCalledTimes(2);
            expect(comp.questionTextRendered).toEqual({ changingThisBreaksApplicationSecurity: '<p>Test Question</p>' });
            expect(comp.sampleSolutions).toEqual([shortAnswerSolution]);
        });
    });

    describe('loadLayout', () => {
        it('should call functions from loadLayout', () => {
            const resetLabelsSpy = vi.spyOn(comp, 'resetLabelsColors');
            const addLastBarSpy = vi.spyOn(comp, 'addLastBarLayout');
            const loadInvalidLayoutSpy = vi.spyOn(comp, 'loadInvalidLayout');

            comp.ngOnInit();
            comp.loadLayout();

            expect(resetLabelsSpy).toHaveBeenCalledTimes(2);
            expect(addLastBarSpy).toHaveBeenCalledTimes(2);
            expect(loadInvalidLayoutSpy).toHaveBeenCalledTimes(2);
        });
    });

    describe('loadData', () => {
        it('should call functions from loadData', () => {
            const resetDataSpy = vi.spyOn(comp, 'resetData');
            const addDataSpy = vi.spyOn(comp, 'addData');
            const updateDataSpy = vi.spyOn(comp, 'updateData');

            comp.ngOnInit();
            comp.loadData();

            expect(resetDataSpy).toHaveBeenCalledTimes(2);
            expect(addDataSpy).toHaveBeenCalledTimes(2);
            expect(updateDataSpy).toHaveBeenCalledTimes(2);
        });
    });

    describe('switchSolution', () => {
        it('should call functions and set values from switchSolution', () => {
            const loadDataInDiagramSpy = vi.spyOn(comp, 'loadDataInDiagram');

            comp.ngOnInit();
            comp.showSolution = true;
            comp.switchSolution();

            expect(loadDataInDiagramSpy).toHaveBeenCalledTimes(2);
            expect(comp.showSolution).toBe(false);
        });
    });

    describe('switchRated', () => {
        it('should call functions and set values from switchRated', () => {
            const loadDataInDiagramSpy = vi.spyOn(comp, 'loadDataInDiagram');

            comp.ngOnInit();
            comp.switchRated();

            expect(loadDataInDiagramSpy).toHaveBeenCalledTimes(2);
            expect(comp.rated).toBe(false);
        });
    });
});
