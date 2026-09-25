import { convertDateFromClient } from 'app/foundation/util/date.utils';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType, ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/quiz/shared/entities/short-answer-solution.model';
import { ShortAnswerMapping } from 'app/quiz/shared/entities/short-answer-mapping.model';
import { toCategoryStrings, toCompetencyLinks } from 'app/quiz/shared/util/quiz-exercise-creation-request.util';
import { UpdateQuizExercise } from 'app/openapi/model/update-quiz-exercise';
import { QuizQuestionFromEditor } from 'app/openapi/model/quiz-question-from-editor';
import { MultipleChoiceQuestionFromEditor } from 'app/openapi/model/multiple-choice-question-from-editor';
import { AnswerOptionFromEditor } from 'app/openapi/model/answer-option-from-editor';
import { DragAndDropQuestionFromEditor } from 'app/openapi/model/drag-and-drop-question-from-editor';
import { DropLocationFromEditor } from 'app/openapi/model/drop-location-from-editor';
import { DragItemFromEditor } from 'app/openapi/model/drag-item-from-editor';
import { DragAndDropMappingFromEditor } from 'app/openapi/model/drag-and-drop-mapping-from-editor';
import { ShortAnswerQuestionFromEditor } from 'app/openapi/model/short-answer-question-from-editor';
import { ShortAnswerSpotFromEditor } from 'app/openapi/model/short-answer-spot-from-editor';
import { ShortAnswerSolutionFromEditor } from 'app/openapi/model/short-answer-solution-from-editor';
import { ShortAnswerMappingFromEditor } from 'app/openapi/model/short-answer-mapping-from-editor';

function getEditorReferenceId(entity: { id?: number; tempID?: number } | undefined): number {
    return entity?.id ?? entity?.tempID ?? 0;
}

function getEditorTempId(entity: { id?: number; tempID?: number } | undefined): number | undefined {
    return entity?.id ?? entity?.tempID;
}

function convertAnswerOptionToUpdateDTO(option: AnswerOption): AnswerOptionFromEditor {
    return {
        id: option.id,
        text: option.text ?? '',
        hint: option.hint,
        explanation: option.explanation,
        isCorrect: option.isCorrect ?? false,
    };
}

function convertMultipleChoiceQuestionToUpdateDTO(question: MultipleChoiceQuestion): MultipleChoiceQuestionFromEditor {
    return {
        type: 'multiple-choice',
        id: question.id,
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        scoringType: question.scoringType ?? ScoringType.ALL_OR_NOTHING,
        randomizeOrder: question.randomizeOrder,
        answerOptions: (question.answerOptions ?? []).map(convertAnswerOptionToUpdateDTO),
        singleChoice: question.singleChoice ?? false,
    };
}

function convertDropLocationToUpdateDTO(dropLocation: DropLocation): DropLocationFromEditor {
    return {
        id: dropLocation.id,
        tempID: getEditorTempId(dropLocation),
        posX: dropLocation.posX ?? 0,
        posY: dropLocation.posY ?? 0,
        width: dropLocation.width ?? 0,
        height: dropLocation.height ?? 0,
    };
}

function convertDragItemToUpdateDTO(dragItem: DragItem): DragItemFromEditor {
    return {
        id: dragItem.id,
        tempID: getEditorTempId(dragItem),
        text: dragItem.text,
        pictureFilePath: dragItem.pictureFilePath,
    };
}

function convertDragAndDropMappingToUpdateDTO(mapping: DragAndDropMapping): DragAndDropMappingFromEditor {
    return {
        id: mapping.id,
        dragItemTempId: getEditorReferenceId(mapping.dragItem),
        dropLocationTempId: getEditorReferenceId(mapping.dropLocation),
    };
}

function convertDragAndDropQuestionToUpdateDTO(question: DragAndDropQuestion): DragAndDropQuestionFromEditor {
    return {
        type: 'drag-and-drop',
        id: question.id,
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        scoringType: question.scoringType ?? ScoringType.PROPORTIONAL_WITH_PENALTY,
        randomizeOrder: question.randomizeOrder,
        backgroundFilePath: question.backgroundFilePath,
        dropLocations: (question.dropLocations ?? []).map(convertDropLocationToUpdateDTO),
        dragItems: (question.dragItems ?? []).map(convertDragItemToUpdateDTO),
        correctMappings: (question.correctMappings ?? []).map(convertDragAndDropMappingToUpdateDTO),
    };
}

function convertShortAnswerSpotToUpdateDTO(spot: ShortAnswerSpot): ShortAnswerSpotFromEditor {
    return {
        id: spot.id,
        tempID: getEditorTempId(spot),
        width: spot.width ?? 0,
        spotNr: spot.spotNr ?? 0,
    };
}

function convertShortAnswerSolutionToUpdateDTO(solution: ShortAnswerSolution): ShortAnswerSolutionFromEditor {
    return {
        id: solution.id,
        tempID: getEditorTempId(solution),
        text: solution.text ?? '',
    };
}

function convertShortAnswerMappingToUpdateDTO(mapping: ShortAnswerMapping): ShortAnswerMappingFromEditor {
    return {
        id: mapping.id,
        solutionTempId: getEditorReferenceId(mapping.solution),
        spotTempId: getEditorReferenceId(mapping.spot),
    };
}

function convertShortAnswerQuestionToUpdateDTO(question: ShortAnswerQuestion): ShortAnswerQuestionFromEditor {
    return {
        type: 'short-answer',
        id: question.id,
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        scoringType: question.scoringType ?? ScoringType.PROPORTIONAL_WITHOUT_PENALTY,
        randomizeOrder: question.randomizeOrder,
        spots: (question.spots ?? []).map(convertShortAnswerSpotToUpdateDTO),
        solutions: (question.solutions ?? []).map(convertShortAnswerSolutionToUpdateDTO),
        correctMappings: (question.correctMappings ?? []).map(convertShortAnswerMappingToUpdateDTO),
        similarityValue: question.similarityValue ?? 85,
        matchLetterCase: question.matchLetterCase ?? false,
    };
}

function convertQuizQuestionToUpdateDTO(question: QuizQuestion): QuizQuestionFromEditor {
    switch (question.type) {
        case QuizQuestionType.MULTIPLE_CHOICE:
            return convertMultipleChoiceQuestionToUpdateDTO(question);
        case QuizQuestionType.DRAG_AND_DROP:
            return convertDragAndDropQuestionToUpdateDTO(question);
        case QuizQuestionType.SHORT_ANSWER:
            return convertShortAnswerQuestionToUpdateDTO(question as ShortAnswerQuestion);
        default:
            throw new Error(`Unsupported quiz question type: ${question.type}`);
    }
}

export function toUpdateQuizExercise(quizExercise: QuizExercise): UpdateQuizExercise {
    return {
        title: quizExercise.title,
        channelName: quizExercise.channelName,
        categories: toCategoryStrings(quizExercise.categories),
        competencyLinks: toCompetencyLinks(quizExercise.competencyLinks) ?? [],
        difficulty: quizExercise.difficulty,
        duration: quizExercise.duration,
        randomizeQuestionOrder: quizExercise.randomizeQuestionOrder,
        quizMode: quizExercise.quizMode,
        quizBatches: quizExercise.quizBatches?.map((batch) => ({ id: batch.id, startTime: convertDateFromClient(batch.startTime), password: batch.password })),
        releaseDate: convertDateFromClient(quizExercise.releaseDate),
        startDate: convertDateFromClient(quizExercise.startDate),
        dueDate: convertDateFromClient(quizExercise.dueDate),
        includedInOverallScore: quizExercise.includedInOverallScore,
        quizQuestions: quizExercise.quizQuestions?.map(convertQuizQuestionToUpdateDTO),
    };
}
