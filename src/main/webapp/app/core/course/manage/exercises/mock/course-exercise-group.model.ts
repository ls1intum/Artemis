import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

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

    /**
     * Optional cap on the points the group can contribute to the course score. Applied at grade
     * calculation: if the summed exercise points exceed the cap, the contribution is capped here.
     */
    maxPoints?: number;

    /**
     * Limits how many exercises a student can hand in for graded assessment. Students may solve
     * all variants (e.g. to obtain AI feedback), but only up to this many count towards the grade.
     */
    handInLimit?: number;

    exercises?: Exercise[];
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

export type GroupTimelineField = 'releaseDate' | 'startDate' | 'dueDate' | 'assessmentDueDate';

/**
 * Resolves the date that actually applies to an exercise for a given timeline field. When the owning
 * group sets that field, the group date wins and the exercise's own date is ignored.
 */
export function effectiveDate(exercise: Exercise, group: CourseExerciseGroup | undefined, field: GroupTimelineField): dayjs.Dayjs | undefined {
    return group?.[field] ?? exercise[field];
}
