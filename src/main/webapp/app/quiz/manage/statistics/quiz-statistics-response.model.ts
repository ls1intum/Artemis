import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizPointStatistic } from 'app/quiz/shared/entities/quiz-point-statistic.model';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizQuestionStatistic } from 'app/quiz/shared/entities/quiz-question-statistic.model';

export interface QuizQuestionWithStatistic extends QuizQuestion {
    statistic: QuizQuestionStatistic;
}

export interface QuizStatisticsOverviewResponse extends QuizExercise {
    quizQuestions?: QuizQuestionWithStatistic[];
    participantsRated?: number;
    participantsUnrated?: number;
}

export interface QuizPointStatisticsResponse extends QuizExercise {
    statistic: QuizPointStatistic;
}

export interface QuizQuestionStatisticResponse extends QuizExercise {
    questionId: number;
    statistic: QuizQuestionStatistic;
}
