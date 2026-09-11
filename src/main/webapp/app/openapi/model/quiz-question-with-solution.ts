import type { MultipleChoiceQuizQuestionWithSolution } from "./multiple-choice-quiz-question-with-solution";
import type { DragAndDropQuizQuestionWithSolution } from "./drag-and-drop-quiz-question-with-solution";
import type { ShortAnswerQuizQuestionWithSolution } from "./short-answer-quiz-question-with-solution";

export type QuizQuestionWithSolution = MultipleChoiceQuizQuestionWithSolution | DragAndDropQuizQuestionWithSolution | ShortAnswerQuizQuestionWithSolution;
