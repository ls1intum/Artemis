import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { DragAndDropSubmittedAnswer } from 'app/quiz/shared/entities/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';

export interface QuizSubmissionFromStudentDTO {
    submittedAnswers: SubmittedAnswerFromStudentDTO[];
}

type SubmittedAnswerFromStudentDTO = MultipleChoiceSubmittedAnswerFromStudentDTO | DragAndDropSubmittedAnswerFromStudentDTO | ShortAnswerSubmittedAnswerFromStudentDTO;

interface MultipleChoiceSubmittedAnswerFromStudentDTO {
    type: 'multiple-choice';
    questionId?: number;
    selectedOptions: number[];
}

interface DragAndDropSubmittedAnswerFromStudentDTO {
    type: 'drag-and-drop';
    questionId?: number;
    mappings: DragAndDropMapping[];
}

interface DragAndDropMapping {
    dragItemId: number;
    dropLocationId: number;
}

interface ShortAnswerSubmittedAnswerFromStudentDTO {
    type: 'short-answer';
    questionId?: number;
    submittedTexts: ShortAnswerSubmittedText[];
}

interface ShortAnswerSubmittedText {
    text: string;
    spotId: number;
}

function createMultipleChoiceSubmittedAnswerFromStudentDTO(submittedAnswer: MultipleChoiceSubmittedAnswer): MultipleChoiceSubmittedAnswerFromStudentDTO {
    return {
        type: 'multiple-choice',
        questionId: submittedAnswer.quizQuestion?.id,
        selectedOptions: submittedAnswer.selectedOptions?.map((option) => option.id!) ?? [],
    };
}

function createDragAndDropSubmittedAnswerFromStudentDTO(submittedAnswer: DragAndDropSubmittedAnswer): DragAndDropSubmittedAnswerFromStudentDTO {
    return {
        type: 'drag-and-drop',
        questionId: submittedAnswer.quizQuestion?.id,
        mappings:
            submittedAnswer.mappings?.map((mapping) => ({
                dragItemId: mapping.dragItem?.id!,
                dropLocationId: mapping.dropLocation?.id!,
            })) ?? [],
    };
}

function createShortAnswerSubmittedAnswerFromStudentDTO(submittedAnswer: ShortAnswerSubmittedAnswer): ShortAnswerSubmittedAnswerFromStudentDTO {
    return {
        type: 'short-answer',
        questionId: submittedAnswer.quizQuestion?.id,
        submittedTexts:
            submittedAnswer.submittedTexts?.map((submittedText) => ({
                text: submittedText.text!,
                spotId: submittedText.spot?.id!,
            })) ?? [],
    };
}

function createSubmittedAnswerFromStudentDTO(submittedAnswer: SubmittedAnswer): SubmittedAnswerFromStudentDTO {
    if (submittedAnswer.type === 'multiple-choice') {
        return createMultipleChoiceSubmittedAnswerFromStudentDTO(submittedAnswer as MultipleChoiceSubmittedAnswer);
    } else if (submittedAnswer.type === 'drag-and-drop') {
        return createDragAndDropSubmittedAnswerFromStudentDTO(submittedAnswer as DragAndDropSubmittedAnswer);
    } else if (submittedAnswer.type === 'short-answer') {
        return createShortAnswerSubmittedAnswerFromStudentDTO(submittedAnswer as ShortAnswerSubmittedAnswer);
    }
    throw new Error('Unknown submitted answer type: ' + submittedAnswer);
}

export function createQuizSubmissionFromStudentDTO(submission: QuizSubmission): QuizSubmissionFromStudentDTO {
    return {
        submittedAnswers: submission.submittedAnswers?.map((submittedAnswer) => createSubmittedAnswerFromStudentDTO(submittedAnswer)) ?? [],
    };
}
