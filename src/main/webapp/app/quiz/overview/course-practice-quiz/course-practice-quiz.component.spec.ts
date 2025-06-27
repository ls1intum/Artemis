import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CoursePracticeQuizComponent } from './course-practice-quiz.component';
import { ActivatedRoute, Router } from '@angular/router';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { MockBuilder } from 'ng-mocks';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { CoursePracticeQuizService } from 'app/quiz/overview/service/course-practice-quiz.service';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { MockInstance } from 'ng-mocks';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { signal } from '@angular/core';

const question1: DragAndDropQuestion = {
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

describe('CoursePracticeQuizComponent', () => {
    MockInstance.scope();
    let component: CoursePracticeQuizComponent;
    let fixture: ComponentFixture<CoursePracticeQuizComponent>;
    let quizService: CoursePracticeQuizService;

    const mockQuestions = [question1, question2, question3];

    beforeEach(async () => {
        await MockBuilder(CoursePracticeQuizComponent)
            .keep(Router)
            .keep(TranslateModule)
            .provide([
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({ courseId: 1 }),
                        },
                    },
                },
            ]);
        quizService = TestBed.inject(CoursePracticeQuizService);
        jest.spyOn(quizService, 'getQuizQuestions').mockReturnValue(of([question1, question2, question3]));
        MockInstance(DragAndDropQuestionComponent, 'secureImageComponent', signal({} as SecuredImageComponent));

        fixture = TestBed.createComponent(CoursePracticeQuizComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should extract courseId from route params', () => {
        expect(component.courseId()).toBe(1);
    });

    it('should load questions from service', () => {
        expect(component.questionsSignal()).toEqual(mockQuestions);
        expect(component.questions()).toEqual(mockQuestions);
    });

    it('should check if questions is empty', () => {
        jest.spyOn(component, 'questions').mockReturnValue([]);
        component.currentIndex.set(1);
        expect(component.isLastQuestion()).toBeTruthy();
        expect(component.currentQuestion()).toBeUndefined();
    });

    it('should check for last question', () => {
        component.currentIndex.set(0);
        expect(component.isLastQuestion()).toBeFalsy();
        component.currentIndex.set(2);
        expect(component.isLastQuestion()).toBeTruthy();
    });

    it('should check for nextQuestion', () => {
        component.currentIndex.set(0);
        component.nextQuestion();
        expect(component.currentIndex()).toBe(1);
        component.currentIndex.set(2);
        const spy = jest.spyOn(component, 'navigateToPractice');
        component.nextQuestion();
        expect(spy).toHaveBeenCalledOnce();
    });

    it('should return the current question based on currentIndex', () => {
        component.currentIndex.set(0);
        expect(component.currentQuestion()).toBe(question1);
    });

    it('should navigate to practice', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        component.navigateToPractice();
        expect(navigateSpy).toHaveBeenCalledWith(['courses', 1, 'practice']);
    });
});
