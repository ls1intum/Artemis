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
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { DragAndDropQuestionStatisticComponent } from 'app/exercises/quiz/manage/statistics/drag-and-drop-question-statistic/drag-and-drop-question-statistic.component';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { DragAndDropQuestionStatistic } from 'app/entities/quiz/drag-and-drop-question-statistic.model';
import { DropLocationCounter } from 'app/entities/quiz/drop-location-counter.model';
import { MockProvider } from 'ng-mocks';
import { ChangeDetectorRef } from '@angular/core';

const route = { params: of({ courseId: 2, exerciseId: 42, questionId: 1 }) };
const dropLocation1 = { posX: 5, invalid: false, tempID: 1 } as DropLocation;
const dropLocation2 = { posX: 0, invalid: false, tempID: 2 } as DropLocation;
const dropLocationCounter = { dropLocation: dropLocation1, ratedCounter: 0, unRatedCounter: 0 } as DropLocationCounter;
const questionStatistic = { dropLocation: dropLocation1, dropLocationCounters: [dropLocationCounter] } as DragAndDropQuestionStatistic;
const question = { id: 1, dropLocations: [dropLocation1, dropLocation2], quizQuestionStatistic: questionStatistic } as DragAndDropQuestion;
const course = { id: 2 } as Course;
let quizExercise = { id: 42, quizStarted: true, course, quizQuestions: [question] } as QuizExercise;

describe('QuizExercise Drag And Drop Question Statistic Component', () => {
    let comp: DragAndDropQuestionStatisticComponent;
    let fixture: ComponentFixture<DragAndDropQuestionStatisticComponent>;
    let quizService: QuizExerciseService;
    let accountService: AccountService;
    let accountSpy: jest.SpyInstance;
    let quizServiceFindSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [DragAndDropQuestionStatisticComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(ChangeDetectorRef),
            ],
        })
            .overrideTemplate(DragAndDropQuestionStatisticComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DragAndDropQuestionStatisticComponent);
                comp = fixture.componentInstance;
                quizService = fixture.debugElement.injector.get(QuizExerciseService);
                accountService = fixture.debugElement.injector.get(AccountService);
                quizServiceFindSpy = jest.spyOn(quizService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
            });
    });

    afterEach(() => {
        quizExercise = { id: 42, quizStarted: true, course, quizQuestions: [question] } as QuizExercise;
    });

    describe('onInit', () => {
        it('should call functions on Init', () => {
            accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const loadQuizSpy = jest.spyOn(comp, 'loadQuiz');
            comp.websocketChannelForData = '';

            comp.ngOnInit();

            expect(accountSpy).toHaveBeenCalledTimes(2);
            expect(quizServiceFindSpy).toHaveBeenCalledWith(42);
            expect(loadQuizSpy).toHaveBeenCalledWith(quizExercise, false);
            expect(comp.websocketChannelForData).toBe('/topic/statistic/42');
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
            const orderDropLocationSpy = jest.spyOn(comp, 'orderDropLocationByPos');
            const resetLabelsSpy = jest.spyOn(comp, 'resetLabelsColors');
            const addLastBarSpy = jest.spyOn(comp, 'addLastBarLayout');
            const loadInvalidLayoutSpy = jest.spyOn(comp, 'loadInvalidLayout');

            comp.ngOnInit();
            comp.loadLayout();

            expect(orderDropLocationSpy).toHaveBeenCalledTimes(2);
            expect(resetLabelsSpy).toHaveBeenCalledTimes(2);
            expect(addLastBarSpy).toHaveBeenCalledTimes(2);
            expect(loadInvalidLayoutSpy).toHaveBeenCalledTimes(2);
        });
    });

    describe('loadData', () => {
        it('should call functions from loadData', () => {
            accountSpy = jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const resetDataSpy = jest.spyOn(comp, 'resetData');
            const updateDataSpy = jest.spyOn(comp, 'updateData');

            comp.ngOnInit();
            comp.loadData();

            expect(resetDataSpy).toHaveBeenCalledTimes(2);
            expect(updateDataSpy).toHaveBeenCalledTimes(2);
        });
    });
});
