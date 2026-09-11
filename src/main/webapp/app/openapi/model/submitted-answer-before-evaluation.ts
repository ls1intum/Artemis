import type { MultipleChoiceSubmittedAnswerBeforeEvaluation } from "./multiple-choice-submitted-answer-before-evaluation";
import type { DragAndDropSubmittedAnswerBeforeEvaluation } from "./drag-and-drop-submitted-answer-before-evaluation";
import type { ShortAnswerSubmittedAnswerBeforeEvaluation } from "./short-answer-submitted-answer-before-evaluation";

export type SubmittedAnswerBeforeEvaluation = MultipleChoiceSubmittedAnswerBeforeEvaluation | DragAndDropSubmittedAnswerBeforeEvaluation | ShortAnswerSubmittedAnswerBeforeEvaluation;
