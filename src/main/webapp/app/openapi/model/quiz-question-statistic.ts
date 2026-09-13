import type { ShortAnswerQuestionStatistic } from './short-answer-question-statistic';
import type { DragAndDropQuestionStatistic } from './drag-and-drop-question-statistic';
import type { MultipleChoiceQuestionStatistic } from './multiple-choice-question-statistic';
export type QuizQuestionStatistic = MultipleChoiceQuestionStatistic | DragAndDropQuestionStatistic | ShortAnswerQuestionStatistic;
