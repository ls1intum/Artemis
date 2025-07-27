import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTrainingQuizComponent } from './course-training-quiz.component';
import { ActivatedRoute, Router } from '@angular/router';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { MockBuilder } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { CourseTrainingQuizService } from '../service/course-training-quiz.service';
import { MockSyncStorage } from 'src/test/javascript/spec/helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { SessionStorageService } from 'ngx-webstorage';
import { Result } from '../../../exercise/shared/entities/result/result.model';
import { AlertService } from '../../../shared/service/alert.service';
import { CourseManagementService } from '../../../core/course/manage/services/course-management.service';
import { MultipleChoiceSubmittedAnswer } from '../../shared/entities/multiple-choice-submitted-answer.model';
import { DragAndDropSubmittedAnswer } from '../../shared/entities/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from '../../shared/entities/short-answer-submitted-answer.model';
import * as Utils from 'app/shared/util/utils';
import { MockInstance } from 'ng-mocks';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { signal } from '@angular/core';

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

const result: Result = { id: 1, submission: { submittedAnswers: [{ scoreInPoints: 2 }] } as any };

describe('CourseTrainingQuizComponent', () => {
    MockInstance(DragAndDropQuestionComponent, 'secureImageComponent', signal({} as SecuredImageComponent));
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
                { provide: SessionStorageService, useClass: MockSyncStorage },
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
        jest.spyOn(quizService, 'getQuizQuestions').mockReturnValue(of([question1, question2, question3]));
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
        expect(component.questionsSignal()).toEqual(mockQuestions);
        expect(component.questions()).toEqual(mockQuestions);
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
        expect(component.currentQuestion()).toBe(question1);
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
        const submitSpy = jest.spyOn(TestBed.inject(CourseTrainingQuizService), 'submitForTraining').mockReturnValue(of(new HttpResponse({ body: result })));
        const showResultSpy = jest.spyOn(component, 'showResult');
        // Drag and Drop
        jest.spyOn(component, 'currentQuestion').mockReturnValue({ ...question1, exerciseId: 1 } as any);
        component.currentIndex.set(0);
        component.onSubmit();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(component.isSubmitting).toBeFalse();
        expect(component.submitted).toBeTrue();
        expect(showResultSpy).toHaveBeenCalledWith(result);
        jest.clearAllMocks();
        // Multiple Choice
        jest.spyOn(component, 'currentQuestion').mockReturnValue({ ...question2, exerciseId: 2 } as any);
        component.currentIndex.set(1);
        component.onSubmit();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(component.isSubmitting).toBeFalse();
        expect(component.submitted).toBeTrue();
        expect(showResultSpy).toHaveBeenCalledWith(result);
        jest.clearAllMocks();
        // Short Answer
        jest.spyOn(component, 'currentQuestion').mockReturnValue({ ...question3, exerciseId: 3 } as any);
        component.currentIndex.set(2);
        component.onSubmit();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(component.isSubmitting).toBeFalse();
        expect(component.submitted).toBeTrue();
        expect(showResultSpy).toHaveBeenCalledWith(result);
    });

    it('should show a warning if no exerciseId is present', () => {
        const alertSpy = jest.spyOn(TestBed.inject(AlertService), 'addAlert');
        jest.spyOn(component, 'currentQuestion').mockReturnValue({ id: 1 } as any);
        component.onSubmit();
        expect(alertSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                message: 'error.noExerciseIdForQuestion',
            }),
        );
        expect(component.isSubmitting).toBeFalse();
    });

    it('should handle submit error', () => {
        const alertSpy = jest.spyOn(TestBed.inject(AlertService), 'addAlert');
        jest.spyOn(component, 'currentQuestion').mockReturnValue({ ...question1, exerciseId: 1 } as any);
        const error = new HttpErrorResponse({
            error: 'error',
            status: 400,
            headers: new HttpHeaders({ 'X-artemisApp-message': 'Fehler beim Absenden' }),
            statusText: 'Bad Request',
        });
        jest.spyOn(TestBed.inject(CourseTrainingQuizService), 'submitForTraining').mockReturnValue(throwError(() => error));
        component.currentIndex.set(2);
        component.onSubmit();
        expect(alertSpy).toHaveBeenCalled();
        expect(component.isSubmitting).toBeFalse();
    });

    it('should applySubmission for multiple choice', () => {
        component.currentIndex.set(1);
        const answer = new MultipleChoiceSubmittedAnswer();
        answer.selectedOptions = [{ id: 1 } as any];
        answer.quizQuestion = question2;
        component.submission.submittedAnswers = [answer];
        component.applySubmission();
        expect(component.selectedAnswerOptions).toEqual([{ id: 1 }]);
    });

    it('should applySubmission for drag and drop', () => {
        component.currentIndex.set(0);
        const answer = new DragAndDropSubmittedAnswer();
        answer.mappings = [{ id: 2 } as any];
        answer.quizQuestion = question1;
        component.submission.submittedAnswers = [answer];
        component.applySubmission();
        expect(component.dragAndDropMappings).toEqual([{ id: 2 }]);
    });

    it('should applySubmission for short answer', () => {
        component.currentIndex.set(2);
        const answer = new ShortAnswerSubmittedAnswer();
        answer.submittedTexts = [{ id: 3 } as any];
        answer.quizQuestion = question3;
        component.submission.submittedAnswers = [answer];
        component.applySubmission();
        expect(component.shortAnswerSubmittedTexts).toEqual([{ id: 3 }]);
    });

    it('should show result and calculate score', () => {
        const result: Result = { id: 1, submission: { submittedAnswers: [{ scoreInPoints: 1, quizQuestion: question1 }] } as any };
        component.currentIndex.set(0);
        component.submission.submittedAnswers = [{ scoreInPoints: 1, quizQuestion: question1 }];
        const roundSpy = jest.spyOn(Utils, 'roundValueSpecifiedByCourseSettings');
        component.showResult(result);
        expect(component.result).toBe(result);
        expect(component.showingResult).toBeTrue();
        expect(roundSpy).toHaveBeenCalledOnce();
        expect(roundSpy).toHaveBeenCalledWith(1, course);
    });

    it('should navigate to practice', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        component.navigateToTraining();
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['courses', 1, 'training']);
    });
});
