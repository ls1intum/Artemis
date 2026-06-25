import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { convertDateFromServer } from 'app/foundation/util/date.utils';

/**
 * Course-level grouping of exercises. Distinct from the exam-scoped `ExerciseGroup`.
 *
 * Exercises within a group are implicit variants of one another: they cover the same course
 * topic and differ only in difficulty, time effort, and application theme (e.g. the same loop
 * pattern from the lecture, themed around cars vs. planes). Variant relationships are not enforced.
 *
 * Mock-only for now — no server-side counterpart yet.
 */
export class CourseExerciseGroup {
    id?: number;
    title?: string;

    /** Explicit display order within the course (drives drag-and-drop reordering). */
    order?: number;

    /**
     * Optional group-level timeline. When a date is set on the group, it applies to ALL exercises
     * in the group and the corresponding individual exercise date is ignored.
     */
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    assessmentDueDate?: dayjs.Dayjs;
    exampleSolutionPublicationDate?: dayjs.Dayjs;
    /** Only relevant while the group's members are programming exercises. */
    buildAndTestStudentSubmissionsAfterDueDate?: dayjs.Dayjs;

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
                buildAndTestStudentSubmissionsAfterDueDate: convertDateFromServer(reference.buildAndTestStudentSubmissionsAfterDueDate),
                exercises: [],
            };
            groupsById.set(reference.id, group);
        }
        group.exercises!.push(exercise);
    }
    return Array.from(groupsById.values());
}

/** Directed relation type between exercises and/or groups. Kept deliberately simple, no payload. */
export enum ExerciseRelationType {
    /** `source` must be completed before `target` (source is a prerequisite of target). */
    PREREQUISITE = 'PREREQUISITE',
    /** `source` is harder than `target`. */
    HARDER_THAN = 'HARDER_THAN',
}

/** A relation endpoint is either an individual exercise or a whole group. */
export enum ExerciseRelationEndpointKind {
    EXERCISE = 'EXERCISE',
    GROUP = 'GROUP',
}

export interface ExerciseRelationEndpoint {
    kind: ExerciseRelationEndpointKind;
    /** Exercise id when kind === EXERCISE, group id when kind === GROUP. */
    id: number;
}

/**
 * Simple directed relation between two endpoints. A group-level endpoint applies to all variants
 * in the group — e.g. variants that differ only thematically share the same prerequisites.
 */
export class ExerciseRelation {
    id?: number;
    type?: ExerciseRelationType;
    source?: ExerciseRelationEndpoint;
    target?: ExerciseRelationEndpoint;
}

export type GroupTimelineField = 'releaseDate' | 'startDate' | 'dueDate' | 'assessmentDueDate' | 'exampleSolutionPublicationDate' | 'buildAndTestStudentSubmissionsAfterDueDate';

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
    // buildAndTestStudentSubmissionsAfterDueDate only exists on ProgrammingExercise, not on the base Exercise type.
    return (exercise as unknown as Record<GroupTimelineField, dayjs.Dayjs | undefined>)[field];
}
