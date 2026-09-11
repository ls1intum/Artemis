import type { MultipleChoiceSubmittedAnswerFromStudent } from "./multiple-choice-submitted-answer-from-student";
import type { DragAndDropSubmittedAnswerFromStudent } from "./drag-and-drop-submitted-answer-from-student";
import type { ShortAnswerSubmittedAnswerFromStudent } from "./short-answer-submitted-answer-from-student";

export type SubmittedAnswerFromStudent = MultipleChoiceSubmittedAnswerFromStudent | DragAndDropSubmittedAnswerFromStudent | ShortAnswerSubmittedAnswerFromStudent;
