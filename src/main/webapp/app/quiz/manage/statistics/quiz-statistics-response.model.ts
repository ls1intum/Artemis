import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizPointStatistic } from 'app/quiz/shared/entities/quiz-point-statistic.model';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizQuestionStatistic } from 'app/quiz/shared/entities/quiz-question-statistic.model';
import { QuestionStatistics } from 'app/openapi/model/question-statistics';

/*
 * The models the statistics views work on: the converted quiz exercise, plus the statistic its endpoint adds.
 * The wire shapes are the generated QuizStatisticsOverview, QuizPointStatistics and QuizQuestionStatisticResponse.
 */

/**
 * The calculated overview statistics for a quiz exercise.
 *
 * The overview sends a summary per question, not the question itself: only what the chart and the maximum score read.
 */
export interface QuizStatisticsOverviewResponse extends Omit<QuizExercise, 'quizQuestions'> {
    quizQuestions?: QuestionStatistics[];
    participantsRated?: number;
    participantsUnrated?: number;
}

/**
 * The calculated point distribution for a quiz exercise.
 */
export interface QuizPointStatisticsResponse extends QuizExercise {
    quizPointStatistic?: QuizPointStatistic;
}

/**
 * The calculated statistic for one question in a quiz exercise.
 */
export interface QuizQuestionStatisticResponse extends Omit<QuizExercise, 'quizQuestions'> {
    quizQuestion?: QuizQuestion;
    quizQuestionStatistic?: QuizQuestionStatistic;
}
