import { TestBed } from '@angular/core/testing';
import { ArtemisQuizService } from 'app/shared/quiz/quiz.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

describe('Quiz Service', () => {
    let quizService: ArtemisQuizService;
    let quizExercise: QuizExercise;

    beforeEach(() => {
        quizService = new ArtemisQuizService();
        quizExercise = new QuizExercise(undefined, undefined);
    });

    it('randomizes order of Questions', () => {
    });
});
