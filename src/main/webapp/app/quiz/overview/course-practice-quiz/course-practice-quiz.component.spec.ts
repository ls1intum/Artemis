import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CoursePracticeQuizComponent } from './course-practice-quiz.component';
import { ActivatedRoute, Router } from '@angular/router';
import { QuizQuestion, QuizQuestionType } from '../../shared/entities/quiz-question.model';
import { MultipleChoiceQuestion } from '../../shared/entities/multiple-choice-question.model';
import { AnswerOption } from '../../shared/entities/answer-option.model';
import { CoursePracticeQuizService } from './course-practice-quiz.service';
import { MockBuilder } from 'ng-mocks';
import { of } from 'rxjs';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';

const question1: QuizQuestion = {
    id: 1,
    type: QuizQuestionType.DRAG_AND_DROP,
    points: 1,
    invalid: false,
    exportQuiz: false,
    randomizeOrder: true,
};
const question2: MultipleChoiceQuestion = {
    id: 2,
    type: QuizQuestionType.MULTIPLE_CHOICE,
    points: 2,
    answerOptions: [{ id: 1 } as AnswerOption],
    invalid: false,
    exportQuiz: false,
    randomizeOrder: true,
};
const question3: QuizQuestion = {
    id: 3,
    type: QuizQuestionType.SHORT_ANSWER,
    points: 3,
    invalid: false,
    exportQuiz: false,
    randomizeOrder: true,
};

describe('CoursePracticeQuizComponent', () => {
    let component: CoursePracticeQuizComponent;
    let fixture: ComponentFixture<CoursePracticeQuizComponent>;

    beforeEach(async () => {
        await MockBuilder(CoursePracticeQuizComponent)
            .keep(Router)
            .keep(HttpClientTestingModule)
            .keep(TranslateModule)
            .provide({
                provide: ActivatedRoute,
                useValue: {
                    parent: { params: of({ courseId: 1 }) },
                },
            });

        fixture = TestBed.createComponent(CoursePracticeQuizComponent);
        component = fixture.componentInstance;
        component.questions = [question1, question2, question3];
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initiate', () => {
        expect(component.courseId).toBe(1);
    });

    it('should load questions on loadQuestions', () => {
        const quizService = TestBed.inject(CoursePracticeQuizService);
        const mockQuestions = [question1, question2, question3];
        jest.spyOn(quizService, 'getQuizQuestions').mockReturnValue(of(mockQuestions));
        component.loadQuestions(1);
        expect(quizService.getQuizQuestions).toHaveBeenCalledWith(1);
        expect(component.questions).toEqual(mockQuestions);
    });

    it('should check for last question', () => {
        component.questions = [question1, question2, question3];
        component.currentIndex = 0;
        expect(component.isLastQuestion).toBeFalse();
        component.currentIndex = 2;
        expect(component.isLastQuestion).toBeTrue();
    });

    it('should check for nextQuestion', () => {
        component.currentIndex = 0;
        component.nextQuestion();
        expect(component.currentIndex).toBe(1);
        component.currentIndex = 2;
        const spy = jest.spyOn(component, 'navigateToPractice');
        component.nextQuestion();
        expect(spy).toHaveBeenCalled();
    });

    it('should check for current question', () => {
        component.questions = [question1, question2, question3];
        component.currentIndex = 0;
        expect(component.currentQuestion).toBe(question1);
    });

    it('should navigate to practice', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        component.navigateToPractice();
        expect(navigateSpy).toHaveBeenCalledWith(['courses', 1, 'practice']);
    });
});
