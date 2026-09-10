import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizPointStatistic } from 'app/quiz/shared/entities/quiz-point-statistic.model';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizQuestionStatistic } from 'app/quiz/shared/entities/quiz-question-statistic.model';

type QuizExerciseWithoutQuestions = Omit<QuizExercise, 'quizQuestions'>;

/**
 * A quiz question together with its calculated statistic.
 */
export interface QuizQuestionWithStatistic extends QuizQuestion {
    quizQuestionStatistic?: QuizQuestionStatistic;
}

/**
 * The calculated overview statistics for a quiz exercise.
 */
export interface QuizStatisticsOverviewResponse extends QuizExercise {
    quizQuestions?: QuizQuestionWithStatistic[];
    participantsRated?: number;
    participantsUnrated?: number;
}

/**
 * The calculated point distribution for a quiz exercise.
 */
export interface QuizPointStatisticsResponse extends QuizExercise {
    quizPointStatistic: QuizPointStatistic;
}

/**
 * The calculated statistic for one question in a quiz exercise.
 */
export interface QuizQuestionStatisticResponse extends QuizExerciseWithoutQuestions {
    quizQuestion: QuizQuestion;
    quizQuestionStatistic: QuizQuestionStatistic;
}
