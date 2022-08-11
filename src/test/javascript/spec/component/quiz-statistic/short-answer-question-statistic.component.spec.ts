import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ShortAnswerQuestionStatisticComponent } from 'app/exercises/quiz/manage/statistics/short-answer-question-statistic/short-answer-question-statistic.component';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerQuestionStatistic } from 'app/entities/quiz/short-answer-question-statistic.model';
import { ShortAnswerSpotCounter } from 'app/entities/quiz/short-answer-spot-counter.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { MockProvider } from 'ng-mocks';
import { ChangeDetectorRef } from '@angular/core';

const route = { params: of({ courseId: 1, exerciseId: 4, questionId: 1 }) };
const answerSpot = { posX: 5, invalid: false, id: 1, tempID: 2 } as ShortAnswerSpot;
const shortAnswerCounter = { spot: answerSpot } as ShortAnswerSpotCounter;
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
    let accountSpy: jest.SpyInstance;
    let quizServiceFindSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ShortAnswerQuestionStatisticComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(ChangeDetectorRef),
            ],
        })
            .overrideTemplate(ShortAnswerQuestionStatisticComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ShortAnswerQuestionStatisticComponent);
                comp = fixture.componentInstance;
                quizService = fixture.debugElement.injector.get(QuizExerciseService);
                accountService = fixture.debugElement.injector.get(AccountService);
                quizServiceFindSpy = jest.spyOn(quizService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
            });
    });

    afterEach(() => {
        quizExercise = {
            id: 4,
            started: true,
            course,
            quizQuestions: [question],
            adjustedDueDate: undefined,
            numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()],
            studentAssignedTeamIdComputed: false,
            secondCorrectionEnabled: true,
        } as QuizExercise;
    });

    describe('OnInit', () => {
        it('should call functions on Init', () => {
            accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const loadQuizSpy = jest.spyOn(comp, 'loadQuiz');
            comp.websocketChannelForData = '';

            comp.ngOnInit();

            expect(accountSpy).toHaveBeenCalled();
            expect(quizServiceFindSpy).toHaveBeenCalledWith(4);
            expect(loadQuizSpy).toHaveBeenCalledWith(quizExercise, false);
            expect(comp.websocketChannelForData).toBe('/topic/statistic/4');
        });

        it('should not load Quiz if not authorised', () => {
            accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(false);
            const loadQuizSpy = jest.spyOn(comp, 'loadQuiz');

            comp.ngOnInit();

            expect(accountSpy).toHaveBeenCalled();
            expect(quizServiceFindSpy).not.toHaveBeenCalled();
            expect(loadQuizSpy).not.toHaveBeenCalled();
        });
    });

    describe('loadQuiz', () => {
        it('should call functions from loadQuiz', () => {
            accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const generateStructureSpy = jest.spyOn(comp, 'generateShortAnswerStructure');
            const generateLettersSpy = jest.spyOn(comp, 'generateLettersForSolutions');
            const loadLayoutSpy = jest.spyOn(comp, 'loadLayout');
            const loadDataSpy = jest.spyOn(comp, 'loadData');

            comp.ngOnInit();
            comp.loadQuiz(quizExercise, false);

            expect(generateStructureSpy).toHaveBeenCalled();
            expect(generateLettersSpy).toHaveBeenCalled();
            expect(loadLayoutSpy).toHaveBeenCalled();
            expect(loadDataSpy).toHaveBeenCalled();
            expect(comp.questionTextRendered).toEqual({ changingThisBreaksApplicationSecurity: '<p>Test Question</p>' });
            expect(comp.sampleSolutions).toEqual([shortAnswerSolution]);
        });
    });

    describe('loadLayout', () => {
        it('should call functions from loadLayout', () => {
            const resetLabelsSpy = jest.spyOn(comp, 'resetLabelsColors');
            const addLastBarSpy = jest.spyOn(comp, 'addLastBarLayout');
            const loadInvalidLayoutSpy = jest.spyOn(comp, 'loadInvalidLayout');

            comp.ngOnInit();
            comp.loadLayout();

            expect(resetLabelsSpy).toHaveBeenCalled();
            expect(addLastBarSpy).toHaveBeenCalled();
            expect(loadInvalidLayoutSpy).toHaveBeenCalled();
        });
    });

    describe('loadData', () => {
        it('should call functions from loadData', () => {
            const resetDataSpy = jest.spyOn(comp, 'resetData');
            const addDataSpy = jest.spyOn(comp, 'addData');
            const updateDataSpy = jest.spyOn(comp, 'updateData');

            comp.ngOnInit();
            comp.loadData();

            expect(resetDataSpy).toHaveBeenCalled();
            expect(addDataSpy).toHaveBeenCalled();
            expect(updateDataSpy).toHaveBeenCalled();
        });
    });

    describe('switchSolution', () => {
        it('should call functions and set values from switchSolution', () => {
            const loadDataInDiagramSpy = jest.spyOn(comp, 'loadDataInDiagram');

            comp.ngOnInit();
            comp.showSolution = true;
            comp.switchSolution();

            expect(loadDataInDiagramSpy).toHaveBeenCalled();
            expect(comp.showSolution).toBeFalse();
        });
    });

    describe('switchRated', () => {
        it('should call functions and set values from switchRated', () => {
            const loadDataInDiagramSpy = jest.spyOn(comp, 'loadDataInDiagram');

            comp.ngOnInit();
            comp.switchRated();

            expect(loadDataInDiagramSpy).toHaveBeenCalled();
            expect(comp.rated).toBeFalse();
        });
    });
});
