import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { convertDateFromServer } from 'app/foundation/util/date.utils';

/**
 * Course-level grouping of exercises, distinct from the exam-scoped `ExerciseGroup`. Members are implicit
 * (unenforced) variants of one another, differing only in difficulty, time effort and theme.
 */
export class CourseExerciseGroup {
    id?: number;
    title?: string;

    /** `'variant'` (a plain ExerciseVariantGroup) or `'milestone'` (a MilestoneExerciseGroup). */
    type?: 'variant' | 'milestone';
    /** Only set when {@link type} is `'milestone'` — the id of the group's anchor MilestoneExercise. */
    milestoneExerciseId?: number;

    /** Explicit display order within the course (drives drag-and-drop reordering). */
    order?: number;

    /**
     * Optional group-level timeline; a date set here governs every member and overrides its individual date.
     * "Build and test after due date" is excluded on purpose: LocalCI derives it per exercise from its build plan.
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
 * Rebuilds the variant groups from the {@link Exercise.exerciseVariantGroup} reference the dashboard already
 * carries, so student views need no extra request. Ungrouped exercises are ignored.
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
                type: reference.type,
                milestoneExerciseId: reference.milestoneExerciseId,
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
 * The date that actually applies for a timeline field. A group governs its members fully — its date wins even
 * when unset — so only ungrouped exercises fall back to their own.
 */
export function effectiveDate(exercise: Exercise, group: CourseExerciseGroup | undefined, field: GroupTimelineField): dayjs.Dayjs | undefined {
    if (group) {
        return group[field];
    }
    return exercise[field];
}
