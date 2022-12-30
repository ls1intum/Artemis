import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisTestModule } from '../test.module';
import { QuizManageUtil } from 'app/exercises/quiz/shared/quiz-manage-util.service';
import { QuizBatch, QuizExercise, QuizMode, QuizStatus } from 'app/entities/quiz/quiz-exercise.model';

describe('ShortAnswerQuestionUtil', () => {
    let quizManageUtil: QuizManageUtil;
    let quizExercise: QuizExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule],
        });
        quizManageUtil = TestBed.inject(QuizManageUtil);
        quizExercise = new QuizExercise(undefined, undefined);
        quizExercise.id = 1;
        quizExercise.title = 'test';
        quizExercise.duration = 600;
        quizExercise.quizMode = QuizMode.SYNCHRONIZED;
        quizExercise.status = QuizStatus.VISIBLE;
        quizExercise.isAtLeastEditor = true;
        quizExercise.quizEnded = false;
    });

    it('should return true if new quiz', () => {
        quizExercise.id = undefined;
        expect(quizManageUtil.isQuizEditable(quizExercise)).toBeTrue();
    });

    it('should return true if existing quiz is synchronized, not active and not over', () => {
        expect(quizManageUtil.isQuizEditable(quizExercise)).toBeTrue();
    });

    it('should return true if existing quiz is batched, no batch exists, not active, at least editor and not over', () => {
        quizExercise.quizMode = QuizMode.BATCHED;
        quizExercise.quizBatches = undefined;
        expect(quizManageUtil.isQuizEditable(quizExercise)).toBeTrue();
    });

    it('should return false if existing quiz is batched, batch exists, not active, at least editor and not over', () => {
        quizExercise.quizMode = QuizMode.BATCHED;
        quizExercise.quizBatches = [new QuizBatch()];
        expect(quizManageUtil.isQuizEditable(quizExercise)).toBeFalse();
    });

    it('should return false if existing quiz is synchronized, active, at least editor, and not over', () => {
        quizExercise.quizMode = QuizMode.SYNCHRONIZED;
        quizExercise.status = QuizStatus.ACTIVE;
        expect(quizManageUtil.isQuizEditable(quizExercise)).toBeFalse();
    });

    it('should return false if existing quiz is synchronized, not active, at least editor, and over', () => {
        quizExercise.quizMode = QuizMode.SYNCHRONIZED;
        quizExercise.quizEnded = true;
        expect(quizManageUtil.isQuizEditable(quizExercise)).toBeFalse();
    });

    it('should return false if existing quiz is synchronized, not active, not editor and not over', () => {
        quizExercise.quizMode = QuizMode.SYNCHRONIZED;
        quizExercise.isAtLeastEditor = false;
        expect(quizManageUtil.isQuizEditable(quizExercise)).toBeFalse();
    });
});
