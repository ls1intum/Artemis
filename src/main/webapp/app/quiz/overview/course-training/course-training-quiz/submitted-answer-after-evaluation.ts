import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';

export class SubmittedAnswerAfterEvaluation {
    scoreInPoints?: number;
    selectedOptions?: AnswerOption[];
    mappings?: DragAndDropMapping[];
    submittedTexts?: ShortAnswerSubmittedText[];
}
