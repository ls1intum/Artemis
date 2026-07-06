import { IconProp } from '@fortawesome/fontawesome-svg-core';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, effectiveDate } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';

/** The available layouts of the exercise management page. */
export type ExerciseManagementView = 'type' | 'week' | 'group' | 'list';

/** One collapsible panel of the exercise management page: a titled slice of the course's exercises. */
export interface Bucket {
    id: string;
    title: string;
    icon?: IconProp;
    group?: CourseExerciseGroup;
    exerciseType?: ExerciseType;
    exercises: Exercise[];
}

/** Resolves a translation key eagerly (the buckets carry ready-to-render titles). */
export type TranslateFn = (key: string, interpolateParams?: object) => string;

/** Everything the bucket builders need from the exercise management page's state. */
export interface BucketContext {
    exercises: Exercise[];
    groups: CourseExerciseGroup[];
    searchTerm: string;
    translate: TranslateFn;
}

const TYPE_ORDER: ExerciseType[] = [ExerciseType.PROGRAMMING, ExerciseType.QUIZ, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD];
const TYPE_TITLE_KEYS: Record<string, string> = {
    [ExerciseType.PROGRAMMING]: 'artemisApp.exerciseManagement.type.PROGRAMMING',
    [ExerciseType.QUIZ]: 'artemisApp.exerciseManagement.type.QUIZ',
    [ExerciseType.MODELING]: 'artemisApp.exerciseManagement.type.MODELING',
    [ExerciseType.TEXT]: 'artemisApp.exerciseManagement.type.TEXT',
    [ExerciseType.FILE_UPLOAD]: 'artemisApp.exerciseManagement.type.FILE_UPLOAD',
};

/** Builds the panels for the requested view from the current exercises, groups and search term. */
export function buildBuckets(view: ExerciseManagementView, context: BucketContext): Bucket[] {
    switch (view) {
        case 'group':
            return buildGroupBuckets(context);
        case 'type':
            return buildTypeBuckets(context);
        case 'week':
            return buildWeekBuckets(context);
        case 'list':
            return buildListBuckets(context);
    }
}

/** The group whose shared timeline governs the exercise, or undefined for ungrouped exercises. */
export function owningGroup(exercise: Exercise, groups: CourseExerciseGroup[]): CourseExerciseGroup | undefined {
    return groups.find((group) => group.exercises?.some((member) => member.id === exercise.id));
}

function hasSearch(context: BucketContext): boolean {
    return context.searchTerm.trim().length > 0;
}

function matches(exercise: Exercise, context: BucketContext): boolean {
    const term = context.searchTerm.trim().toLowerCase();
    return !term || (exercise.title ?? '').toLowerCase().includes(term);
}

function visibleExercises(context: BucketContext): Exercise[] {
    return context.exercises.filter((exercise) => matches(exercise, context));
}

function sortExercises(exercises: Exercise[], context: BucketContext): Exercise[] {
    return [...exercises].sort((a, b) => {
        const da = effectiveDate(a, owningGroup(a, context.groups), 'dueDate');
        const db = effectiveDate(b, owningGroup(b, context.groups), 'dueDate');
        return (da?.valueOf() ?? 0) - (db?.valueOf() ?? 0);
    });
}

function buildListBuckets(context: BucketContext): Bucket[] {
    const exercises = sortExercises(visibleExercises(context), context);
    if (exercises.length === 0) return [];
    return [{ id: 'all', title: context.translate('artemisApp.exerciseManagement.bucket.all'), exercises }];
}

function buildGroupBuckets(context: BucketContext): Bucket[] {
    const groupedIds = new Set<number>();
    const searching = hasSearch(context);
    const buckets: Bucket[] = context.groups
        .slice()
        .sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''))
        .map((group) => {
            const members = group.exercises ?? [];
            members.forEach((exercise) => exercise.id !== undefined && groupedIds.add(exercise.id));
            return {
                id: `group-${group.id}`,
                title: group.title ?? context.translate('artemisApp.exerciseManagement.bucket.group', { id: group.id }),
                group,
                exercises: sortExercises(
                    members.filter((exercise) => matches(exercise, context)),
                    context,
                ),
            };
        })
        .filter((bucket) => !searching || bucket.exercises.length > 0);

    const ungrouped = sortExercises(
        visibleExercises(context).filter((exercise) => exercise.id === undefined || !groupedIds.has(exercise.id)),
        context,
    );
    if (ungrouped.length > 0) {
        buckets.push({ id: 'ungrouped', title: context.translate('artemisApp.exerciseManagement.bucket.ungrouped'), exercises: ungrouped });
    }
    return buckets;
}

function buildTypeBuckets(context: BucketContext): Bucket[] {
    return TYPE_ORDER.map((type) => ({
        id: `type-${type}`,
        title: TYPE_TITLE_KEYS[type] ? context.translate(TYPE_TITLE_KEYS[type]) : type,
        icon: getIcon(type),
        exerciseType: type,
        exercises: sortExercises(
            visibleExercises(context).filter((exercise) => exercise.type === type),
            context,
        ),
    })).filter((bucket) => bucket.exercises.length > 0);
}

function buildWeekBuckets(context: BucketContext): Bucket[] {
    const startOf = (exercise: Exercise): dayjs.Dayjs | undefined =>
        effectiveDate(exercise, owningGroup(exercise, context.groups), 'startDate') ?? effectiveDate(exercise, owningGroup(exercise, context.groups), 'releaseDate');

    const visible = visibleExercises(context);
    const dated = visible.filter((exercise) => startOf(exercise));
    const undated = visible.filter((exercise) => !startOf(exercise));

    const base = dated.reduce<dayjs.Dayjs | undefined>((min, exercise) => {
        const date = startOf(exercise)!;
        return !min || date.isBefore(min) ? date : min;
    }, undefined);

    const byWeek = new Map<number, Exercise[]>();
    for (const exercise of dated) {
        const weekIndex = base ? startOf(exercise)!.diff(base, 'week') : 0;
        let list = byWeek.get(weekIndex);
        if (!list) {
            list = [];
            byWeek.set(weekIndex, list);
        }
        list.push(exercise);
    }

    const buckets: Bucket[] = [...byWeek.keys()]
        .sort((a, b) => a - b)
        .map((weekIndex) => ({
            id: `week-${weekIndex}`,
            title: context.translate('artemisApp.exerciseManagement.bucket.week', { number: weekIndex + 1 }),
            exercises: sortExercises(byWeek.get(weekIndex)!, context),
        }));

    if (undated.length > 0) {
        buckets.push({ id: 'unscheduled', title: context.translate('artemisApp.exerciseManagement.bucket.unscheduled'), exercises: sortExercises(undated, context) });
    }
    return buckets;
}
