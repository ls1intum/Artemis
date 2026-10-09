import type dayjs from 'dayjs/esm';
import type { CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import type { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import type { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import type { QuizExerciseCreate } from 'app/openapi/model/quiz-exercise-create';
import { toQuizQuestionRequest } from 'app/quiz/shared/util/quiz-question-request.util';
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
        quizMode: exercise.quizMode ?? 'SYNCHRONIZED',
        duration: exercise.duration ?? 0,
        quizBatches: exercise.quizBatches?.map((batch) => ({ startTime: toDateString(batch.startTime)! })),
        // New parts have no ids yet; references between them travel as client-side temporary ids.
        quizQuestions: (exercise.quizQuestions ?? []).map((question) => toQuizQuestionRequest(question, () => undefined)),
    };
}
