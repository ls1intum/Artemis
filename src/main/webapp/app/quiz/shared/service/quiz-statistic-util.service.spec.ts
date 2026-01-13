import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockProvider } from 'ng-mocks';
import { QuizStatisticUtil } from './quiz-statistic-util.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exam } from 'app/exam/shared/entities/exam.model';

describe('QuizStatisticUtil', () => {
    setupTestBed({ zoneless: true });

    let service: QuizStatisticUtil;
    let router: Router;

    const course: Course = { id: 1 } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [QuizStatisticUtil, MockProvider(Router)],
        });

        service = TestBed.inject(QuizStatisticUtil);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('getBaseUrlForQuizExercise', () => {
        it('should return course URL for non-exam quiz', () => {
            const quizExercise: QuizExercise = {
                id: 123,
                course: course,
            } as QuizExercise;

            const result = service.getBaseUrlForQuizExercise(quizExercise);

            expect(result).toBe('/course-management/1/quiz-exercises/123');
        });

        it('should return exam URL for exam quiz', () => {
            const exam: Exam = { id: 10, course: course } as Exam;
            const exerciseGroup: ExerciseGroup = { id: 20, exam: exam } as ExerciseGroup;
            const quizExercise: QuizExercise = {
                id: 123,
                exerciseGroup: exerciseGroup,
            } as QuizExercise;

            const result = service.getBaseUrlForQuizExercise(quizExercise);

            expect(result).toBe('/course-management/1/exams/10/exercise-groups/20/quiz-exercises/123');
        });
    });

    describe('previousStatistic', () => {
        it('should navigate to quiz-point-statistic when at first question', () => {
            const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockReturnValue(Promise.resolve(true));

            const question1: QuizQuestion = { id: 1, type: QuizQuestionType.MULTIPLE_CHOICE } as QuizQuestion;
            const quizExercise: QuizExercise = {
                id: 123,
                course: course,
                quizQuestions: [question1],
            } as QuizExercise;

            service.previousStatistic(quizExercise, question1);

            expect(navigateSpy).toHaveBeenCalledWith('/course-management/1/quiz-exercises/123/quiz-point-statistic');
        });

        it('should navigate to previous question statistic', () => {
            const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockReturnValue(Promise.resolve(true));

            const question1: QuizQuestion = { id: 1, type: QuizQuestionType.MULTIPLE_CHOICE } as QuizQuestion;
            const question2: QuizQuestion = { id: 2, type: QuizQuestionType.DRAG_AND_DROP } as QuizQuestion;
            const quizExercise: QuizExercise = {
                id: 123,
                course: course,
                quizQuestions: [question1, question2],
            } as QuizExercise;

            service.previousStatistic(quizExercise, question2);

            expect(navigateSpy).toHaveBeenCalledWith('/course-management/1/quiz-exercises/123/mc-question-statistic/1');
        });
    });

    describe('nextStatistic', () => {
        it('should navigate to quiz-point-statistic when at last question', () => {
            const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockReturnValue(Promise.resolve(true));

            const question1: QuizQuestion = { id: 1, type: QuizQuestionType.MULTIPLE_CHOICE } as QuizQuestion;
            const quizExercise: QuizExercise = {
                id: 123,
                course: course,
                quizQuestions: [question1],
            } as QuizExercise;

            service.nextStatistic(quizExercise, question1);

            expect(navigateSpy).toHaveBeenCalledWith('/course-management/1/quiz-exercises/123/quiz-point-statistic');
        });

        it('should navigate to next question statistic', () => {
            const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockReturnValue(Promise.resolve(true));

            const question1: QuizQuestion = { id: 1, type: QuizQuestionType.MULTIPLE_CHOICE } as QuizQuestion;
            const question2: QuizQuestion = { id: 2, type: QuizQuestionType.DRAG_AND_DROP } as QuizQuestion;
            const quizExercise: QuizExercise = {
                id: 123,
                course: course,
                quizQuestions: [question1, question2],
            } as QuizExercise;

            service.nextStatistic(quizExercise, question1);

            expect(navigateSpy).toHaveBeenCalledWith('/course-management/1/quiz-exercises/123/dnd-question-statistic/2');
        });
    });

    describe('navigateToStatisticOf', () => {
        it('should navigate to mc-question-statistic for multiple choice', () => {
            const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockReturnValue(Promise.resolve(true));

            const question: QuizQuestion = { id: 1, type: QuizQuestionType.MULTIPLE_CHOICE } as QuizQuestion;
            const quizExercise: QuizExercise = {
                id: 123,
                course: course,
            } as QuizExercise;

            service.navigateToStatisticOf(quizExercise, question);

            expect(navigateSpy).toHaveBeenCalledWith('/course-management/1/quiz-exercises/123/mc-question-statistic/1');
        });

        it('should navigate to dnd-question-statistic for drag and drop', () => {
            const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockReturnValue(Promise.resolve(true));

            const question: QuizQuestion = { id: 2, type: QuizQuestionType.DRAG_AND_DROP } as QuizQuestion;
            const quizExercise: QuizExercise = {
                id: 123,
                course: course,
            } as QuizExercise;

            service.navigateToStatisticOf(quizExercise, question);

            expect(navigateSpy).toHaveBeenCalledWith('/course-management/1/quiz-exercises/123/dnd-question-statistic/2');
        });

        it('should navigate to sa-question-statistic for short answer', () => {
            const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockReturnValue(Promise.resolve(true));

            const question: QuizQuestion = { id: 3, type: QuizQuestionType.SHORT_ANSWER } as QuizQuestion;
            const quizExercise: QuizExercise = {
                id: 123,
                course: course,
            } as QuizExercise;

            service.navigateToStatisticOf(quizExercise, question);

            expect(navigateSpy).toHaveBeenCalledWith('/course-management/1/quiz-exercises/123/sa-question-statistic/3');
        });

        it('should not navigate for unknown question type', () => {
            const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockReturnValue(Promise.resolve(true));

            const question: QuizQuestion = { id: 4, type: 'UNKNOWN' as QuizQuestionType } as QuizQuestion;
            const quizExercise: QuizExercise = {
                id: 123,
                course: course,
            } as QuizExercise;

            service.navigateToStatisticOf(quizExercise, question);

            expect(navigateSpy).not.toHaveBeenCalled();
        });

        it('should use exam URL for exam quiz', () => {
            const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockReturnValue(Promise.resolve(true));

            const exam: Exam = { id: 10, course: course } as Exam;
            const exerciseGroup: ExerciseGroup = { id: 20, exam: exam } as ExerciseGroup;
            const question: QuizQuestion = { id: 1, type: QuizQuestionType.MULTIPLE_CHOICE } as QuizQuestion;
            const quizExercise: QuizExercise = {
                id: 123,
                exerciseGroup: exerciseGroup,
            } as QuizExercise;

            service.navigateToStatisticOf(quizExercise, question);

            expect(navigateSpy).toHaveBeenCalledWith('/course-management/1/exams/10/exercise-groups/20/quiz-exercises/123/mc-question-statistic/1');
        });
    });
});
