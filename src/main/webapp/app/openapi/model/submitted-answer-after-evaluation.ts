import type { ShortAnswerSubmittedAnswerAfterEvaluation } from './short-answer-submitted-answer-after-evaluation';
import type { DragAndDropSubmittedAnswerAfterEvaluation } from './drag-and-drop-submitted-answer-after-evaluation';
import type { MultipleChoiceSubmittedAnswerAfterEvaluation } from './multiple-choice-submitted-answer-after-evaluation';

export type SubmittedAnswerAfterEvaluation = MultipleChoiceSubmittedAnswerAfterEvaluation | DragAndDropSubmittedAnswerAfterEvaluation | ShortAnswerSubmittedAnswerAfterEvaluation;
