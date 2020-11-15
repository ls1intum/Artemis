import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { DragAndDropQuestionStatisticComponent } from 'app/exercises/quiz/manage/statistics/drag-and-drop-question-statistic/drag-and-drop-question-statistic.component';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { DragAndDropQuestionStatistic } from 'app/entities/quiz/drag-and-drop-question-statistic.model';
import { DropLocationCounter } from 'app/entities/quiz/drop-location-counter.model';

const route = { params: of({ courseId: 2, exerciseId: 42, questionId: 1 }) };
const dropLocation = { posX: 5, invalid: false, tempID: 1 } as DropLocation;
const dropLocationCounter = { dropLocation: dropLocation, ratedCounter: 0, unRatedCounter: 0 } as DropLocationCounter;
const questionStatistic = { dropLocation: dropLocation, dropLocationCounters: [dropLocationCounter] } as DragAndDropQuestionStatistic;
const question = { id: 1, dropLocations: [dropLocation, { posX: 0, invalid: false, tempID: 2 } as DropLocation], quizQuestionStatistic: questionStatistic } as DragAndDropQuestion;
const course = { id: 2 } as Course;
let quizExercise = { id: 42, started: true, course, quizQuestions: [question], adjustedDueDate: undefined } as QuizExercise;

describe('QuizExercise Question Statistic Component', () => {
    let comp: DragAndDropQuestionStatisticComponent;
    let fixture: ComponentFixture<DragAndDropQuestionStatisticComponent>;
    let quizService: QuizExerciseService;
    let accountService: AccountService;
    let accountSpy: jasmine.Spy;
    let router: Router;
    let translateService: TranslateService;
    let quizServiceFindSpy: jasmine.Spy;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [DragAndDropQuestionStatisticComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .overrideTemplate(DragAndDropQuestionStatisticComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DragAndDropQuestionStatisticComponent);
                comp = fixture.componentInstance;
                quizService = fixture.debugElement.injector.get(QuizExerciseService);
                accountService = fixture.debugElement.injector.get(AccountService);
                router = fixture.debugElement.injector.get(Router);
                translateService = fixture.debugElement.injector.get(TranslateService);
                quizServiceFindSpy = spyOn(quizService, 'find').and.returnValue(of(new HttpResponse({ body: quizExercise })));
            });
    });

    afterEach(() => {
        quizExercise = { id: 42, started: true, course, quizQuestions: [question], adjustedDueDate: undefined } as QuizExercise;
    });

    describe('OnInit', function () {
        it('should call functions on Init', () => {
            accountSpy = spyOn(accountService, 'hasAnyAuthorityDirect').and.returnValue(true);
            const loadQuizSpy = spyOn(comp, 'loadQuiz');
            comp.websocketChannelForData = '';

            comp.ngOnInit();

            expect(accountSpy).toHaveBeenCalled();
            expect(quizServiceFindSpy).toHaveBeenCalledWith(42);
            expect(loadQuizSpy).toHaveBeenCalledWith(quizExercise, false);
            expect(comp.websocketChannelForData).toEqual('/topic/statistic/42');
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
            const orderDropLocationSpy = spyOn(comp, 'orderDropLocationByPos');
            const resetLabelsSpy = spyOn(comp, 'resetLabelsColors');
            const addLastBarSpy = spyOn(comp, 'addLastBarLayout');
            const loadInvalidLayoutSpy = spyOn(comp, 'loadInvalidLayout');

            comp.ngOnInit();
            comp.loadLayout();

            expect(orderDropLocationSpy).toHaveBeenCalled();
            expect(resetLabelsSpy).toHaveBeenCalled();
            expect(addLastBarSpy).toHaveBeenCalled();
            expect(loadInvalidLayoutSpy).toHaveBeenCalled();
        });
    });

    describe('loadData', function () {
        it('should call functions from loadLayout', () => {
            accountSpy = spyOn(accountService, 'hasAnyAuthorityDirect').and.returnValue(true);
            const resetDataSpy = spyOn(comp, 'resetData');
            const updateDataSpy = spyOn(comp, 'updateData');

            comp.ngOnInit();
            comp.loadData();

            expect(resetDataSpy).toHaveBeenCalled();
            expect(updateDataSpy).toHaveBeenCalled();
        });
    });
});
