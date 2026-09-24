import type { ShortAnswerSubmittedTextFromStudent } from './short-answer-submitted-text-from-student';
import type { DragAndDropMappingReEvaluate } from './drag-and-drop-mapping-re-evaluate';

export interface SubmittedAnswerFromStudent {
    type: string;
    questionId: number;
    selectedOptions: Array<number>;
    mappings: Array<DragAndDropMappingReEvaluate>;
    submittedTexts: Array<ShortAnswerSubmittedTextFromStudent>;
}

