import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTrainingQuizComponent } from './course-training-quiz.component';
import { ActivatedRoute, Router } from '@angular/router';
import { QuizQuestion, QuizQuestionType } from '../../../shared/entities/quiz-question.model';
import { MockBuilder } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { CourseTrainingQuizService } from '../../service/course-training-quiz.service';
import { MockTranslateService } from '../../../../../../../test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { SessionStorageService } from '../../../../shared/service/session-storage.service';
import { AlertService } from '../../../../shared/service/alert.service';
import { CourseManagementService } from '../../../../core/course/manage/services/course-management.service';
import { MockInstance } from 'ng-mocks';
import { DragAndDropQuestionComponent } from '../../../shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { ImageComponent } from '../../../../shared/image/image.component';
import { signal } from '@angular/core';
import { SubmittedAnswerAfterEvaluation } from './SubmittedAnswerAfterEvaluation';
import { QuizQuestionTraining } from './quiz-question-training.model';

const question1: QuizQuestion = {
    id: 1,
    type: QuizQuestionType.DRAG_AND_DROP,
    points: 1,
    invalid: false,
    exportQuiz: false,
    randomizeOrder: true,
};
const question2: QuizQuestion = {
    id: 2,
    type: QuizQuestionType.MULTIPLE_CHOICE,
    points: 2,
    invalid: false,
    exportQuiz: false,
    randomizeOrder: true,
};
const question3: QuizQuestion = {
    id: 3,
    type: QuizQuestionType.SHORT_ANSWER,
    points: 3,
    randomizeOrder: false,
    invalid: false,
    exportQuiz: false,
};

const course = { id: 1, title: 'Test Course' };

const answer: SubmittedAnswerAfterEvaluation = { selectedOptions: [{ scoreInPoints: 2 }] };

describe('CourseTrainingQuizComponent', () => {
    MockInstance(DragAndDropQuestionComponent, 'secureImageComponent', signal({} as ImageComponent));
    let component: CourseTrainingQuizComponent;
    let fixture: ComponentFixture<CourseTrainingQuizComponent>;
    let quizService: CourseTrainingQuizService;

    const mockQuestions = [
        { quizQuestionWithSolutionDTO: question1, isRated: false, questionIds: [1], isNewSession: true },
        { quizQuestionWithSolutionDTO: question2, isRated: true, questionIds: [1], isNewSession: true },
        { quizQuestionWithSolutionDTO: question3, isRated: false, questionIds: [1], isNewSession: true },
    ];

    beforeEach(async () => {
        await MockBuilder(CourseTrainingQuizComponent)
            .keep(Router)
            .provide([
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({ courseId: 1 }),
                        },
                    },
                },
            ]);
        quizService = TestBed.inject(CourseTrainingQuizService);
        jest.spyOn(quizService, 'getQuizQuestions').mockReturnValue(
            of(
                new HttpResponse<QuizQuestionTraining[]>({
                    body: mockQuestions,
                    headers: { get: () => '3' } as any,
                }),
            ),
        );
        jest.spyOn(TestBed.inject(CourseManagementService), 'find').mockReturnValue(of(new HttpResponse({ body: course })));

        fixture = TestBed.createComponent(CourseTrainingQuizComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should extract courseId from route params', () => {
        expect(component.courseId()).toBe(1);
    });

    it('should load course from quizManagementService', () => {
        expect(component.course()).toEqual(course);
    });

    it('should load questions from service', () => {
        const mockResponse = new HttpResponse<QuizQuestionTraining[]>({
            body: mockQuestions,
            headers: { get: () => '3' } as any,
        });
        jest.spyOn(quizService, 'getQuizQuestionsPage').mockReturnValue(of(mockResponse));
        component.page.set(0);
        component.loadQuestions();
        expect(component.allLoadedQuestions()).toHaveLength(3);
        expect(component.allLoadedQuestions()[0].quizQuestionWithSolutionDTO).toEqual(question1);
    });

    it('should check for last question', () => {
        component.totalItems.set(3);
        component.allLoadedQuestions.set(mockQuestions);
        component.currentIndex.set(0);
        expect(component.isLastQuestion()).toBeFalsy();
        component.currentIndex.set(2);
        expect(component.isLastQuestion()).toBeTruthy();
    });

    it('should check if questions is empty', () => {
        component.allLoadedQuestions.set([]);
        component.currentIndex.set(1);
        expect(component.isLastQuestion()).toBeTruthy();
        expect(component.currentQuestion()).toBeUndefined();
    });

    it('should return the current question based on currentIndex', () => {
        component.allLoadedQuestions.set(mockQuestions);
        component.currentIndex.set(0);
        expect(component.currentQuestion()).toBe(question1);
    });

    it('should go to the next question and call initQuestion', () => {
        component.allLoadedQuestions.set(mockQuestions);
        component.currentIndex.set(0);
        const initQuestionSpy = jest.spyOn(component, 'initQuestion');
        component.nextQuestion();
        expect(component.currentIndex()).toBe(1);
        expect(initQuestionSpy).toHaveBeenCalledOnce();
        expect(initQuestionSpy).toHaveBeenCalledWith(question2);
    });

    it('should increment page and call loadQuestions when hasNext is true', () => {
        component.page.set(0);
        component.hasNext.set(true);
        const loadQuestionsSpy = jest.spyOn(component, 'loadQuestions');

        component.loadNextPage();

        expect(component.page()).toBe(1);
        expect(loadQuestionsSpy).toHaveBeenCalled();
    });

    it('should not increment page or call loadQuestions when hasNext is false', () => {
        component.page.set(0);
        component.hasNext.set(false);
        const loadQuestionsSpy = jest.spyOn(component, 'loadQuestions');

        component.loadNextPage();

        expect(component.page()).toBe(0);
        expect(loadQuestionsSpy).not.toHaveBeenCalled();
    });

    it('should set allQuestionsLoaded to true when response body is empty', () => {
        const mockResponse = new HttpResponse<QuizQuestionTraining[]>({
            body: [],
            headers: { get: () => '0' } as any,
        });
        jest.spyOn(quizService, 'getQuizQuestionsPage').mockReturnValue(of(mockResponse));

        component.loadQuestions();
        expect(component.hasNext()).toBeFalsy();
    });

    it('should init the current question', () => {
        component.initQuestion(question1);
        expect(component.showingResult).toBeFalsy();
        expect(component.dragAndDropMappings).toEqual([]);
        component.initQuestion(question2);
        expect(component.showingResult).toBeFalsy();
        expect(component.selectedAnswerOptions).toEqual([]);
        component.initQuestion(question3);
        expect(component.showingResult).toBeFalsy();
        expect(component.shortAnswerSubmittedTexts).toEqual([]);
    });

    it('should submit quiz and handle success', () => {
        const submitSpy = jest.spyOn(TestBed.inject(CourseTrainingQuizService), 'submitForTraining').mockReturnValue(of(new HttpResponse({ body: answer })));
        const showResultSpy = jest.spyOn(component, 'applyEvaluatedAnswer');
        // Drag and Drop
        jest.spyOn(component, 'currentQuestion').mockReturnValue(question1);
        component.currentIndex.set(0);
        component.onSubmit();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(component.submitted).toBeTrue();
        expect(showResultSpy).toHaveBeenCalledWith(answer);
        jest.clearAllMocks();
        // Multiple Choice
        jest.spyOn(component, 'currentQuestion').mockReturnValue(question2);
        component.currentIndex.set(1);
        component.onSubmit();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(component.submitted).toBeTrue();
        expect(showResultSpy).toHaveBeenCalledWith(answer);
        jest.clearAllMocks();
        // Short Answer
        jest.spyOn(component, 'currentQuestion').mockReturnValue(question3);
        component.currentIndex.set(2);
        component.onSubmit();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(component.submitted).toBeTrue();
        expect(showResultSpy).toHaveBeenCalledWith(answer);
    });

    it('should show a warning if no questionId is present', () => {
        const alertSpy = jest.spyOn(TestBed.inject(AlertService), 'addAlert');
        jest.spyOn(component, 'currentQuestion').mockReturnValue({ id: null } as any);
        component.onSubmit();
        expect(alertSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                message: 'No questionId found',
            }),
        );
    });

    it('should handle submit error', () => {
        const alertSpy = jest.spyOn(TestBed.inject(AlertService), 'addAlert');
        jest.spyOn(component, 'currentQuestion').mockReturnValue(Object.assign({}, question1) as any);
        const error = new HttpErrorResponse({
            error: 'error',
            status: 400,
            statusText: 'Bad Request',
        });
        jest.spyOn(TestBed.inject(CourseTrainingQuizService), 'submitForTraining').mockReturnValue(throwError(() => error));
        component.currentIndex.set(2);
        component.onSubmit();
        expect(alertSpy).toHaveBeenCalled();
    });

    it('should navigate to practice', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        component.navigateToTraining();
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['courses', 1, 'training']);
    });

    it('should set showUnratedConfirmation to false when confirmUnratedPractice is called', () => {
        component.showUnratedConfirmation = true;
        component.confirmUnratedPractice();
        expect(component.showUnratedConfirmation).toBeFalse();
    });

    it('should set showUnratedConfirmation to false and navigate to training when cancelUnratedPractice is called', () => {
        const navigateSpy = jest.spyOn(component, 'navigateToTraining');
        component.showUnratedConfirmation = true;
        component.cancelUnratedPractice();
        expect(component.showUnratedConfirmation).toBeFalse();
        expect(navigateSpy).toHaveBeenCalled();
    });
});
