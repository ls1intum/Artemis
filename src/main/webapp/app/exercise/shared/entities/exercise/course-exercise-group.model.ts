import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { convertDateFromServer } from 'app/foundation/util/date.utils';

/**
 * Course-level grouping of exercises. Distinct from the exam-scoped `ExerciseGroup`.
 *
 * Exercises within a group are implicit variants of one another: they cover the same course
 * topic and differ only in difficulty, time effort, and application theme (e.g. the same loop
 * pattern from the lecture, themed around cars vs. planes). Variant relationships are not enforced.
 */
export class CourseExerciseGroup {
    id?: number;
    title?: string;

    /** Explicit display order within the course (drives drag-and-drop reordering). */
    order?: number;

    /**
     * Optional group-level timeline. When a date is set on the group, it applies to ALL exercises
     * in the group and the corresponding individual exercise date is ignored.
     *
     * A programming exercise's "build and test after due date" is deliberately absent: on LocalCI that date is
     * derived per exercise from its own build plan, so members of one group legitimately differ. Each member
     * keeps its own, re-derived from the shared due date.
     */
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    assessmentDueDate?: dayjs.Dayjs;
    exampleSolutionPublicationDate?: dayjs.Dayjs;

    /**
     * Optional cap on the points the group can contribute to the course score. Applied at grade
     * calculation: if the summed exercise points exceed the cap, the contribution is capped here.
     */
    maxPoints?: number;

    exercises?: Exercise[];
}

/**
 * Reconstructs the course-level variant groups from a list of exercises by grouping on the
 * embedded {@link Exercise.exerciseVariantGroup} reference. Used by the student views, where the
 * dashboard already carries each (release-filtered) exercise's group, so no extra request is needed.
 * Exercises without a group are ignored. Group metadata (title, dates, caps) is taken from the
 * embedded reference; dates are converted from their server representation to dayjs.
 */
export function buildGroupsFromExercises(exercises: Exercise[]): CourseExerciseGroup[] {
    const groupsById = new Map<number, CourseExerciseGroup>();
    for (const exercise of exercises) {
        const reference = exercise.exerciseVariantGroup;
        if (reference?.id === undefined) {
            continue;
        }
        let group = groupsById.get(reference.id);
        if (!group) {
            group = {
                id: reference.id,
                title: reference.title,
                maxPoints: reference.maxPoints,
                releaseDate: convertDateFromServer(reference.releaseDate),
                startDate: convertDateFromServer(reference.startDate),
                dueDate: convertDateFromServer(reference.dueDate),
                assessmentDueDate: convertDateFromServer(reference.assessmentDueDate),
                exampleSolutionPublicationDate: convertDateFromServer(reference.exampleSolutionPublicationDate),
                exercises: [],
            };
            groupsById.set(reference.id, group);
        }
        (group.exercises ??= []).push(exercise);
    }
    return Array.from(groupsById.values());
}

export type GroupTimelineField = 'releaseDate' | 'startDate' | 'dueDate' | 'assessmentDueDate' | 'exampleSolutionPublicationDate';

/**
 * Resolves the date that actually applies to an exercise for a given timeline field. Once an exercise
 * belongs to a group, the group's timeline fully governs it: the group date is used even when it is
 * unset (the exercise's own date is ignored in that case too). Only ungrouped exercises fall back to
 * their individual dates.
 */
export function effectiveDate(exercise: Exercise, group: CourseExerciseGroup | undefined, field: GroupTimelineField): dayjs.Dayjs | undefined {
    if (group) {
        return group[field];
    }
    return exercise[field];
}
