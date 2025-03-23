import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { QuizPoolComponent } from 'app/quiz/manage/quiz-pool.component';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { QuizPoolService } from 'app/quiz/manage/quiz-pool.service';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { QuizPool } from 'app/quiz/shared/entities/quiz-pool.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { QuizGroup } from 'app/quiz/shared/entities/quiz-group.model';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { QuizPoolMappingComponent } from 'app/quiz/manage/quiz-pool-mapping.component';
import { QuizQuestionListEditComponent } from 'app/quiz/manage/quiz-question-list-edit.component';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { ChangeDetectorRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../helpers/mocks/service/mock-account.service';

describe('QuizPoolComponent', () => {
    let fixture: ComponentFixture<QuizPoolComponent>;
    let component: QuizPoolComponent;
    let quizPoolService: QuizPoolService;
    let examService: ExamManagementService;
    let changeDetectorRef: ChangeDetectorRef;

    const courseId = 1;
    const examId = 2;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId, examId }) }, queryParams: of({}) } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbModule, FormsModule],
            declarations: [
                QuizPoolComponent,
                MockComponent(QuizPoolMappingComponent),
                MockComponent(QuizQuestionListEditComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ActivatedRoute, useValue: route },
                MockProvider(ChangeDetectorRef),
                MockProvider(AlertService),
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizPoolComponent);
                component = fixture.componentInstance;
                quizPoolService = fixture.debugElement.injector.get(QuizPoolService);
                examService = fixture.debugElement.injector.get(ExamManagementService);
                changeDetectorRef = fixture.debugElement.injector.get(ChangeDetectorRef);
                fixture.detectChanges();
            });
    });

    it('should initialize quiz pool', () => {
        const quizPool = new QuizPool();
        const quizGroup = new QuizGroup();
        quizGroup.name = 'Test Group';
        const quizQuestion = new MultipleChoiceQuestion();
        quizQuestion.quizGroup = quizGroup;
        quizPool.id = 1;
        quizPool.quizQuestions = [quizQuestion];
        quizPool.quizGroups = [quizGroup];
        jest.spyOn(quizPoolService, 'find').mockReturnValue(of(new HttpResponse<QuizPool>({ body: quizPool })));
        component.ngOnInit();
        expect(component.quizPool).toEqual(quizPool);
    });

    it('should initialize quiz pool with new object if existing quiz pool is not found', () => {
        jest.spyOn(quizPoolService, 'find').mockReturnValue(of(new HttpResponse<QuizPool>({ body: null })));
        component.ngOnInit();
        expect(component.quizPool.quizGroups).toBeArrayOfSize(0);
        expect(component.quizPool.quizQuestions).toBeArrayOfSize(0);
    });

    it('should set isExamStarted to true', () => {
        const exam = new Exam();
        exam.startDate = dayjs().subtract(1, 'hour');
        jest.spyOn(examService, 'find').mockReturnValue(of(new HttpResponse<Exam>({ body: exam })));
        component.ngOnInit();
        expect(component.isExamStarted).toBeTrue();
    });

    it('should set isExamStarted to false', () => {
        const exam = new Exam();
        exam.startDate = dayjs().add(1, 'hour');
        jest.spyOn(examService, 'find').mockReturnValue(of(new HttpResponse<Exam>({ body: exam })));
        component.ngOnInit();
        expect(component.isExamStarted).toBeFalse();
    });

    it('should call QuizGroupQuestionMappingComponent.addQuestion when there is a new question', () => {
        component.quizPool = new QuizPool();
        const quizPoolMappingFixture = TestBed.createComponent(QuizPoolMappingComponent);
        component.quizPoolMappingComponent = quizPoolMappingFixture.componentInstance;
        const addQuestionSpy = jest.spyOn(component.quizPoolMappingComponent, 'addQuestion');
        const quizQuestion = new MultipleChoiceQuestion();
        component.handleQuestionAdded(quizQuestion);
        expect(addQuestionSpy).toHaveBeenCalledOnce();
        expect(addQuestionSpy).toHaveBeenCalledWith(quizQuestion);
    });

    it('should call QuizGroupQuestionMappingComponent.deleteQuestion when a question is deleted', () => {
        component.quizPool = new QuizPool();
        const quizPoolMappingFixture = TestBed.createComponent(QuizPoolMappingComponent);
        component.quizPoolMappingComponent = quizPoolMappingFixture.componentInstance;
        const deleteQuestionSpy = jest.spyOn(component.quizPoolMappingComponent, 'deleteQuestion');
        const quizQuestion = new MultipleChoiceQuestion();
        component.handleQuestionDeleted(quizQuestion);
        expect(deleteQuestionSpy).toHaveBeenCalledOnce();
        expect(deleteQuestionSpy).toHaveBeenCalledWith(quizQuestion);
    });

    it('should call QuizPoolService.update when saving', () => {
        const quizPool = new QuizPool();
        component.courseId = courseId;
        component.examId = examId;
        component.quizPool = quizPool;
        component.hasPendingChanges = true;
        component.isValid = true;
        const quizQuestionsEditFixture = TestBed.createComponent(QuizQuestionListEditComponent);
        component.quizQuestionsEditComponent = quizQuestionsEditFixture.componentInstance;
        const quizPoolMappingFixture = TestBed.createComponent(QuizPoolMappingComponent);
        component.quizPoolMappingComponent = quizPoolMappingFixture.componentInstance;
        const parseAllQuestionsSpy = jest.spyOn(component.quizQuestionsEditComponent, 'parseAllQuestions').mockImplementation();
        const getMaxPointsSpy = jest.spyOn(component.quizPoolMappingComponent, 'getMaxPoints').mockImplementation();
        const updateQuizPoolSpy = jest.spyOn(quizPoolService, 'update').mockReturnValue(of(new HttpResponse<QuizPool>({ body: quizPool })));
        component.save();
        expect(parseAllQuestionsSpy).toHaveBeenCalledOnce();
        expect(getMaxPointsSpy).toHaveBeenCalledOnce();
        expect(updateQuizPoolSpy).toHaveBeenCalledOnce();
    });

    it('should not call QuizPoolService.update if there is no pending changes or is not valid', () => {
        const quizPool = new QuizPool();
        component.courseId = courseId;
        component.examId = examId;
        component.quizPool = quizPool;
        component.hasPendingChanges = false;
        component.isValid = false;
        const updateQuizPoolSpy = jest.spyOn(quizPoolService, 'update').mockImplementation();
        component.save();
        expect(updateQuizPoolSpy).toHaveBeenCalledTimes(0);
    });

    it('should set isValid to true if all questions and groups are valid', () => {
        const answerOption0 = new AnswerOption();
        answerOption0.isCorrect = true;
        const answerOption1 = new AnswerOption();
        answerOption1.isCorrect = false;
        const question = new MultipleChoiceQuestion();
        question.points = 1;
        question.answerOptions = [answerOption0, answerOption1];
        component.quizPool = new QuizPool();
        component.quizPool.quizQuestions = [question];
        const quizPoolMappingFixture = TestBed.createComponent(QuizPoolMappingComponent);
        component.quizPoolMappingComponent = quizPoolMappingFixture.componentInstance;
        component.isValid = false;
        component.handleUpdate();
        component.isValid = true;
    });

    it('should set isValid to false if at least 1 question is invalid', () => {
        const question = new MultipleChoiceQuestion();
        question.points = -1;
        question.answerOptions = [];
        component.quizPool = new QuizPool();
        component.quizPool.quizQuestions = [question];
        const quizPoolMappingFixture = TestBed.createComponent(QuizPoolMappingComponent);
        component.quizPoolMappingComponent = quizPoolMappingFixture.componentInstance;
        component.isValid = true;
        component.handleUpdate();
        component.isValid = false;
    });

    it('should set invalid reasons when there is a group that does not have any question', () => {
        component.quizPool = new QuizPool();
        const quizPoolMappingFixture = TestBed.createComponent(QuizPoolMappingComponent);
        component.quizPoolMappingComponent = quizPoolMappingFixture.componentInstance;
        jest.spyOn(changeDetectorRef.constructor.prototype, 'detectChanges').mockImplementation();
        jest.spyOn(component.quizPoolMappingComponent, 'hasGroupsWithNoQuestion').mockReturnValue(true);
        jest.spyOn(component.quizPoolMappingComponent, 'getGroupNamesWithNoQuestion').mockReturnValue(['Test Group']);
        jest.spyOn(component.quizPoolMappingComponent, 'hasGroupsWithDifferentQuestionPoints').mockReturnValue(false);
        component.handleUpdate();
        expect(component.invalidReasons).toBeArrayOfSize(1);
        expect(component.invalidReasons[0]).toEqual({
            translateKey: 'artemisApp.quizPool.invalidReasons.groupNoQuestion',
            translateValues: {
                name: 'Test Group',
            },
        });
    });

    it('should set invalid reasons when there is a group whose questions do not have the same points', () => {
        component.quizPool = new QuizPool();
        const quizPoolMappingFixture = TestBed.createComponent(QuizPoolMappingComponent);
        component.quizPoolMappingComponent = quizPoolMappingFixture.componentInstance;
        jest.spyOn(changeDetectorRef.constructor.prototype, 'detectChanges').mockImplementation();
        jest.spyOn(component.quizPoolMappingComponent, 'hasGroupsWithNoQuestion').mockReturnValue(false);
        jest.spyOn(component.quizPoolMappingComponent, 'hasGroupsWithDifferentQuestionPoints').mockReturnValue(true);
        jest.spyOn(component.quizPoolMappingComponent, 'getGroupNamesWithDifferentQuestionPoints').mockReturnValue(['Test Group']);
        component.handleUpdate();
        expect(component.invalidReasons).toBeArrayOfSize(1);
        expect(component.invalidReasons[0]).toEqual({
            translateKey: 'artemisApp.quizPool.invalidReasons.groupHasDifferentQuestionPoints',
            translateValues: {
                name: 'Test Group',
            },
        });
    });
});
