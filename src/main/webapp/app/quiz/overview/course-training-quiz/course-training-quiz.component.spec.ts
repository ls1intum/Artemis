import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTrainingQuizComponent } from './course-training-quiz.component';
import { ActivatedRoute, Router } from '@angular/router';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { MockBuilder } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { CourseTrainingQuizService } from '../service/course-training-quiz.service';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockInstance } from 'ng-mocks';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { ImageComponent } from 'app/shared/image/image.component';
import { signal } from '@angular/core';
import { SubmittedAnswerAfterEvaluation } from './SubmittedAnswerAfterEvaluation';

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

const mockTrainingQuestions = [
    { quizQuestion: question1, isRated: false },
    { quizQuestion: question2, isRated: true },
    { quizQuestion: question3, isRated: false },
];

const course = { id: 1, title: 'Test Course' };

const answer: SubmittedAnswerAfterEvaluation = { selectedOptions: [{ scoreInPoints: 2 }] };

describe('CourseTrainingQuizComponent', () => {
    MockInstance(DragAndDropQuestionComponent, 'secureImageComponent', signal({} as ImageComponent));
    let component: CourseTrainingQuizComponent;
    let fixture: ComponentFixture<CourseTrainingQuizComponent>;
    let quizService: CourseTrainingQuizService;

    const mockQuestions = [question1, question2, question3];

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
        jest.spyOn(quizService, 'getQuizQuestions').mockReturnValue(of(mockTrainingQuestions));
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
        expect(component.questionsSignal()).toEqual(mockTrainingQuestions);
        expect(component.questions()).toEqual(mockTrainingQuestions);
    });

    it('should check for last question', () => {
        component.currentIndex.set(0);
        expect(component.isLastQuestion()).toBeFalsy();
        component.currentIndex.set(2);
        expect(component.isLastQuestion()).toBeTruthy();
    });

    it('should check if questions is empty', () => {
        jest.spyOn(component, 'questions').mockReturnValue([]);
        component.currentIndex.set(1);
        expect(component.isLastQuestion()).toBeTruthy();
        expect(component.currentQuestion()).toBeUndefined();
    });

    it('should return the current question based on currentIndex', () => {
        component.currentIndex.set(0);
        expect(component.currentQuestion()).toBe(mockTrainingQuestions[0]);
    });

    it('should go to the next question and call initQuestion', () => {
        component.currentIndex.set(0);
        const initQuestionSpy = jest.spyOn(component, 'initQuestion');
        component.nextQuestion();
        expect(component.currentIndex()).toBe(1);
        expect(initQuestionSpy).toHaveBeenCalledOnce();
        expect(initQuestionSpy).toHaveBeenCalledWith(question2);
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
        jest.spyOn(component, 'currentQuestion').mockReturnValue({ quizQuestion: question1, isRated: false, exerciseId: 1 } as any);
        component.currentIndex.set(0);
        component.onSubmit();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(component.submitted).toBeTrue();
        expect(showResultSpy).toHaveBeenCalledWith(answer);
        jest.clearAllMocks();
        // Multiple Choice
        jest.spyOn(component, 'currentQuestion').mockReturnValue({ quizQuestion: question1, isRated: false, exerciseId: 1 } as any);
        component.currentIndex.set(1);
        component.onSubmit();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(component.submitted).toBeTrue();
        expect(showResultSpy).toHaveBeenCalledWith(answer);
        jest.clearAllMocks();
        // Short Answer
        jest.spyOn(component, 'currentQuestion').mockReturnValue({ quizQuestion: question3, isRated: false, exerciseId: 3 } as any);
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
        jest.spyOn(component, 'currentQuestion').mockReturnValue({ ...question1 } as any);
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
});
