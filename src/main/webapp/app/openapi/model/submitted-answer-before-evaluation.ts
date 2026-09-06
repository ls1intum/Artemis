import type { AnswerOptionWithoutSolution } from './answer-option-without-solution';
import type { DragAndDropMapping } from './drag-and-drop-mapping';
import type { ShortAnswerSubmittedText } from './short-answer-submitted-text';
import type { QuizQuestionWithoutSolution } from './quiz-question-without-solution';

export interface SubmittedAnswerBeforeEvaluation {
    type: string;
    id?: number;
    quizQuestion?: QuizQuestionWithoutSolution;
    selectedOptions?: Array<AnswerOptionWithoutSolution>;
    mappings?: Array<DragAndDropMapping>;
    submittedTexts?: Array<ShortAnswerSubmittedText>;
}

