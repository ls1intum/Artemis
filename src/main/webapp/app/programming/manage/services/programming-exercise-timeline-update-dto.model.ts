import { convertDateFromClient } from 'app/shared/util/date.utils';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

/**
 * DTO for updating the timeline of a programming exercise.
 * Contains only the date-related fields that can be updated via the timeline endpoint.
 */
export interface ProgrammingExerciseTimelineUpdateDTO {
    id: number;
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentType?: AssessmentType;
    assessmentDueDate?: string;
    exampleSolutionPublicationDate?: string;
    buildAndTestStudentSubmissionsAfterDueDate?: string;
}

/**
 * Converts a ProgrammingExercise entity to a ProgrammingExerciseTimelineUpdateDTO
 * for sending only timeline-related fields to the server.
 *
 * @param exercise the programming exercise to convert
 * @returns a ProgrammingExerciseTimelineUpdateDTO with only timeline fields
 */
export function toProgrammingExerciseTimelineUpdateDTO(exercise: ProgrammingExercise): ProgrammingExerciseTimelineUpdateDTO {
    return {
        id: exercise.id!,
        releaseDate: convertDateFromClient(exercise.releaseDate),
        startDate: convertDateFromClient(exercise.startDate),
        dueDate: convertDateFromClient(exercise.dueDate),
        assessmentType: exercise.assessmentType,
        assessmentDueDate: convertDateFromClient(exercise.assessmentDueDate),
        exampleSolutionPublicationDate: convertDateFromClient(exercise.exampleSolutionPublicationDate),
        buildAndTestStudentSubmissionsAfterDueDate: convertDateFromClient(exercise.buildAndTestStudentSubmissionsAfterDueDate),
    };
}
