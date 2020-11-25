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
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MultipleChoiceQuestionStatisticComponent } from 'app/exercises/quiz/manage/statistics/multiple-choice-question-statistic/multiple-choice-question-statistic.component';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { MultipleChoiceQuestionStatistic } from 'app/entities/quiz/multiple-choice-question-statistic.model';
import { AnswerCounter } from 'app/entities/quiz/answer-counter.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';

const route = { params: of({ courseId: 3, exerciseId: 22, questionId: 1 }) };
const answerOption1 = { id: 5 } as AnswerOption;
const answerCounter = { answer: answerOption1 } as AnswerCounter;
const questionStatistic = { answerCounters: [answerCounter] } as MultipleChoiceQuestionStatistic;
const question = { id: 1, answerOptions: [answerOption1], quizQuestionStatistic: questionStatistic } as MultipleChoiceQuestion;
const course = { id: 3 } as Course;
let quizExercise = { id: 22, started: true, course, quizQuestions: [question], adjustedDueDate: undefined } as QuizExercise;

describe('QuizExercise Multiple Choice Question Statistic Component', () => {
    let comp: MultipleChoiceQuestionStatisticComponent;
    let fixture: ComponentFixture<MultipleChoiceQuestionStatisticComponent>;
    let quizService: QuizExerciseService;
    let accountService: AccountService;
    let accountSpy: jasmine.Spy;
    let quizServiceFindSpy: jasmine.Spy;

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
            ],
        })
            .overrideTemplate(MultipleChoiceQuestionStatisticComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MultipleChoiceQuestionStatisticComponent);
                comp = fixture.componentInstance;
                quizService = fixture.debugElement.injector.get(QuizExerciseService);
                accountService = fixture.debugElement.injector.get(AccountService);
                quizServiceFindSpy = spyOn(quizService, 'find').and.returnValue(of(new HttpResponse({ body: quizExercise })));
            });
    });

    afterEach(() => {
        quizExercise = { id: 22, started: true, course, quizQuestions: [question], adjustedDueDate: undefined } as QuizExercise;
    });

    describe('OnInit', function () {
        it('should call functions on Init', () => {
            accountSpy = spyOn(accountService, 'hasAnyAuthorityDirect').and.returnValue(true);
            const loadQuizSpy = spyOn(comp, 'loadQuiz');
            comp.websocketChannelForData = '';

            comp.ngOnInit();

            expect(accountSpy).toHaveBeenCalled();
            expect(quizServiceFindSpy).toHaveBeenCalledWith(22);
            expect(loadQuizSpy).toHaveBeenCalledWith(quizExercise, false);
            expect(comp.websocketChannelForData).toEqual('/topic/statistic/22');
        });

        it('should not load Quiz if not authorised', () => {
            accountSpy = spyOn(accountService, 'hasAnyAuthorityDirect').and.returnValue(false);
            const loadQuizSpy = spyOn(comp, 'loadQuiz');

            comp.ngOnInit();

            expect(accountSpy).toHaveBeenCalled();
            expect(quizServiceFindSpy).not.toHaveBeenCalled();
            expect(loadQuizSpy).not.toHaveBeenCalled();
        });
    });

    describe('loadLayout', function () {
        it('should call functions from loadLayout', () => {
            accountSpy = spyOn(accountService, 'hasAnyAuthorityDirect').and.returnValue(true);
            const resetLabelsSpy = spyOn(comp, 'resetLabelsColors');
            const addLastBarSpy = spyOn(comp, 'addLastBarLayout');
            const loadInvalidLayoutSpy = spyOn(comp, 'loadInvalidLayout');
            const loadSolutionSpy = spyOn(comp, 'loadSolutionLayout');

            comp.ngOnInit();
            comp.loadLayout();

            expect(resetLabelsSpy).toHaveBeenCalled();
            expect(addLastBarSpy).toHaveBeenCalled();
            expect(loadInvalidLayoutSpy).toHaveBeenCalled();
            expect(loadSolutionSpy).toHaveBeenCalled();
        });
    });

    describe('loadData', function () {
        it('should call functions from loadData', () => {
            accountSpy = spyOn(accountService, 'hasAnyAuthorityDirect').and.returnValue(true);
            const resetDataSpy = spyOn(comp, 'resetData');
            const addDataSpy = spyOn(comp, 'addData');
            const updateDataSpy = spyOn(comp, 'updateData');

            comp.ngOnInit();
            comp.loadData();

            expect(resetDataSpy).toHaveBeenCalled();
            expect(addDataSpy).toHaveBeenCalled();
            expect(updateDataSpy).toHaveBeenCalled();
        });
    });
});
