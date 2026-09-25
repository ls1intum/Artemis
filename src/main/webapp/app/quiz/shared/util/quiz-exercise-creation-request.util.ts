import type dayjs from 'dayjs/esm';
import type { CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import type { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import type { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { type QuizQuestion, QuizQuestionType, ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import type { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import type { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import type { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import type { QuizExerciseCreate } from 'app/openapi/model/quiz-exercise-create';
import type { QuizQuestionCreate } from 'app/openapi/model/quiz-question-create';
import type { MultipleChoiceQuestionCreate } from 'app/openapi/model/multiple-choice-question-create';
import type { DragAndDropQuestionCreate } from 'app/openapi/model/drag-and-drop-question-create';
import type { ShortAnswerQuestionCreate } from 'app/openapi/model/short-answer-question-create';
import type { CompetencyLink } from 'app/openapi/model/competency-link';

/*
 * The Playwright suite loads this module in Node to create quiz exercises through the API. Node cannot resolve
 * 'dayjs/esm', so nothing here may import it, or a module that imports it, at runtime. That is why the date
 * conversion below does not use convertDateFromClient and the defaults are written as literals, not enum members.
 */

/**
 * Serializes a date for a request. The Playwright suite passes ISO strings where the class declares dayjs, so both
 * are accepted.
 *
 * @param date the date to send
 * @returns the ISO string, or undefined for a missing or invalid date
 */
function toDateString(date: dayjs.Dayjs | string | undefined): string | undefined {
    if (typeof date === 'string') {
        return date;
    }
    return date?.isValid() ? date.toJSON() : undefined;
}

/**
 * Converts competency links into the request model, which references each competency by id only.
 *
 * @param links the links of the exercise being saved
 * @returns the request model of the links
 */
export function toCompetencyLinks(links: CompetencyExerciseLink[] | undefined): CompetencyLink[] | undefined {
    return links?.map((link) => ({ competency: { id: link.competency?.id }, weight: link.weight ?? 1 }));
}

/**
 * Serializes categories the way the server stores them: one JSON document per category.
 *
 * @param categories the categories of the exercise being saved
 * @returns the serialized categories
 */
export function toCategoryStrings(categories: ExerciseCategory[] | undefined): string[] | undefined {
    return categories?.map((category) => JSON.stringify(category));
}

/**
 * Turns the editor's upload map into the file parts of a multipart request.
 *
 * The server matches each uploaded file to the question that references it by the part's file name, which is the map
 * key. The Blob itself may carry a different name, or none, so every part is renamed to its key.
 *
 * @param files the uploads keyed by the file name the questions reference
 * @returns the files to send, each named by its key
 */
export function toNamedFiles(files: Map<string, Blob>): File[] {
    return Array.from(files, ([fileName, file]) => new File([file], fileName, { type: file.type }));
}

function toMultipleChoiceQuestionCreate(question: MultipleChoiceQuestion): MultipleChoiceQuestionCreate {
    return {
        type: 'multiple-choice',
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        scoringType: question.scoringType ?? ScoringType.ALL_OR_NOTHING,
        randomizeOrder: question.randomizeOrder,
        answerOptions: (question.answerOptions ?? []).map((option) => ({
            text: option.text ?? '',
            hint: option.hint,
            explanation: option.explanation,
            isCorrect: option.isCorrect ?? false,
        })),
        singleChoice: question.singleChoice ?? false,
    };
}

function toDragAndDropQuestionCreate(question: DragAndDropQuestion): DragAndDropQuestionCreate {
    return {
        type: 'drag-and-drop',
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        scoringType: question.scoringType ?? ScoringType.ALL_OR_NOTHING,
        randomizeOrder: question.randomizeOrder,
        backgroundFilePath: question.backgroundFilePath,
        dropLocations: (question.dropLocations ?? []).map((dropLocation) => ({
            tempID: dropLocation.tempID ?? 0,
            posX: dropLocation.posX ?? 0,
            posY: dropLocation.posY ?? 0,
            width: dropLocation.width ?? 0,
            height: dropLocation.height ?? 0,
        })),
        dragItems: (question.dragItems ?? []).map((dragItem) => ({
            tempID: dragItem.tempID ?? 0,
            text: dragItem.text,
            pictureFilePath: dragItem.pictureFilePath,
        })),
        correctMappings: (question.correctMappings ?? []).map((mapping) => ({
            dragItemTempId: mapping.dragItem?.tempID ?? 0,
            dropLocationTempId: mapping.dropLocation?.tempID ?? 0,
        })),
    };
}

function toShortAnswerQuestionCreate(question: ShortAnswerQuestion): ShortAnswerQuestionCreate {
    return {
        type: 'short-answer',
        title: question.title ?? '',
        text: question.text,
        hint: question.hint,
        explanation: question.explanation,
        points: question.points ?? 0,
        // The server requires a scoring type. A question imported from a file may lack one, so fall back to the default
        // the editor assigns to a new short-answer question.
        scoringType: question.scoringType ?? ScoringType.PROPORTIONAL_WITHOUT_PENALTY,
        randomizeOrder: question.randomizeOrder,
        spots: (question.spots ?? []).map((spot) => ({
            tempID: spot.tempID ?? 0,
            spotNr: spot.spotNr ?? 0,
            width: spot.width ?? 0,
        })),
        solutions: (question.solutions ?? []).map((solution) => ({
            tempID: solution.tempID ?? 0,
            text: solution.text ?? '',
        })),
        correctMappings: (question.correctMappings ?? []).map((mapping) => ({
            solutionTempId: mapping.solution?.tempID ?? 0,
            spotTempId: mapping.spot?.tempID ?? 0,
        })),
        similarityValue: question.similarityValue ?? 85,
        matchLetterCase: question.matchLetterCase ?? false,
    };
}

/**
 * Converts a question into the request model of the creation endpoints.
 *
 * New questions have no ids yet, so every reference between their parts travels as a client-side temporary id.
 *
 * @param question the question to create
 * @returns the request model of the question
 * @throws Error if the question carries no recognised type
 */
export function toQuizQuestionCreate(question: QuizQuestion): QuizQuestionCreate {
    switch (question.type) {
        case QuizQuestionType.MULTIPLE_CHOICE:
            return toMultipleChoiceQuestionCreate(question);
        case QuizQuestionType.DRAG_AND_DROP:
            return toDragAndDropQuestionCreate(question);
        case QuizQuestionType.SHORT_ANSWER:
            return toShortAnswerQuestionCreate(question as ShortAnswerQuestion);
        default:
            throw new Error(`Unsupported quiz question type: ${question.type}`);
    }
}

/**
 * Converts a quiz exercise into the request model of the creation endpoints.
 *
 * @param exercise the exercise to create
 * @returns the request model of the exercise
 */
export function toQuizExerciseCreate(exercise: QuizExercise): QuizExerciseCreate {
    return {
        title: exercise.title ?? '',
        releaseDate: toDateString(exercise.releaseDate),
        startDate: toDateString(exercise.startDate),
        dueDate: toDateString(exercise.dueDate),
        difficulty: exercise.difficulty,
        mode: exercise.mode ?? 'INDIVIDUAL',
        includedInOverallScore: exercise.includedInOverallScore ?? 'INCLUDED_COMPLETELY',
        competencyLinks: toCompetencyLinks(exercise.competencyLinks),
        categories: toCategoryStrings(exercise.categories),
        channelName: exercise.channelName,
        randomizeQuestionOrder: exercise.randomizeQuestionOrder ?? true,
        quizMode: exercise.quizMode ?? 'INDIVIDUAL',
        duration: exercise.duration ?? 0,
        quizBatches: exercise.quizBatches?.map((batch) => ({ startTime: toDateString(batch.startTime)! })),
        quizQuestions: (exercise.quizQuestions ?? []).map(toQuizQuestionCreate),
    };
}
