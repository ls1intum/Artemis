import type { ShortAnswerQuizQuestionStatistic } from './short-answer-quiz-question-statistic';
import type { DragAndDropQuizQuestionStatistic } from './drag-and-drop-quiz-question-statistic';
import type { MultipleChoiceQuizQuestionStatistic } from './multiple-choice-quiz-question-statistic';
export type QuizQuestionStatistic = MultipleChoiceQuizQuestionStatistic | DragAndDropQuizQuestionStatistic | ShortAnswerQuizQuestionStatistic;
