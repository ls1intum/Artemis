import { IconProp } from '@fortawesome/fontawesome-svg-core';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, effectiveDate } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';

/** The available layouts of the exercise management page. */
export type ExerciseManagementView = 'type' | 'week' | 'group' | 'list';

/** One collapsible panel of the exercise management page: a titled slice of the course's exercises. */
export interface CourseExerciseCard {
    id: string;
    title: string;
    icon?: IconProp;
    group?: CourseExerciseGroup;
    exerciseType?: ExerciseType;
    exercises: Exercise[];
}

/** Resolves a translation key eagerly (the cards carry ready-to-render titles). */
export type TranslateFn = (key: string, interpolateParams?: object) => string;

/** Everything the card builders need from the exercise management page's state. */
export interface CourseExerciseCardContext {
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

/** The context enriched with a precomputed exercise-id → owning-group lookup, so the hot paths avoid re-scanning. */
interface ResolvedContext extends CourseExerciseCardContext {
    readonly owningGroupByExerciseId: ReadonlyMap<number, CourseExerciseGroup>;
}

/** Builds the panels for the requested view from the current exercises, groups and search term. */
export function buildCourseExerciseCards(view: ExerciseManagementView, context: CourseExerciseCardContext): CourseExerciseCard[] {
    const resolved = resolveContext(context);
    switch (view) {
        case 'group':
            return buildGroupCards(resolved);
        case 'type':
            return buildTypeCards(resolved);
        case 'week':
            return buildWeekCards(resolved);
        case 'list':
            return buildListCards(resolved);
    }
}

/**
 * Precomputes the exercise-id → owning-group lookup once so the sort comparator and week grouping resolve an
 * exercise's group in O(1) instead of re-scanning every group (and its members) on each comparison.
 */
function resolveContext(context: CourseExerciseCardContext): ResolvedContext {
    const owningGroupByExerciseId = new Map<number, CourseExerciseGroup>();
    for (const group of context.groups) {
        for (const member of group.exercises ?? []) {
            if (member.id !== undefined) {
                owningGroupByExerciseId.set(member.id, group);
            }
        }
    }
    return {
        exercises: context.exercises,
        groups: context.groups,
        searchTerm: context.searchTerm,
        translate: context.translate,
        owningGroupByExerciseId,
    };
}

/** The group whose shared timeline governs the exercise, or undefined for ungrouped exercises. */
export function owningGroup(exercise: Exercise, groups: CourseExerciseGroup[]): CourseExerciseGroup | undefined {
    return groups.find((group) => group.exercises?.some((member) => member.id === exercise.id));
}

/** O(1) equivalent of {@link owningGroup} using the precomputed lookup, for use inside comparators and hot loops. */
function owningGroupOf(exercise: Exercise, context: ResolvedContext): CourseExerciseGroup | undefined {
    return exercise.id === undefined ? undefined : context.owningGroupByExerciseId.get(exercise.id);
}

function hasSearch(context: CourseExerciseCardContext): boolean {
    return context.searchTerm.trim().length > 0;
}

function matches(exercise: Exercise, context: CourseExerciseCardContext): boolean {
    const term = context.searchTerm.trim().toLowerCase();
    return !term || (exercise.title ?? '').toLowerCase().includes(term);
}

function visibleExercises(context: CourseExerciseCardContext): Exercise[] {
    return context.exercises.filter((exercise) => matches(exercise, context));
}

function sortExercises(exercises: Exercise[], context: ResolvedContext): Exercise[] {
    return [...exercises].sort((a, b) => {
        const da = effectiveDate(a, owningGroupOf(a, context), 'dueDate');
        const db = effectiveDate(b, owningGroupOf(b, context), 'dueDate');
        // Exercises without an (effective) due date sort last rather than as if due at the epoch, so an undated
        // exercise is not presented as the most urgent one.
        if (da === undefined && db === undefined) return 0;
        if (da === undefined) return 1;
        if (db === undefined) return -1;
        return da.valueOf() - db.valueOf();
    });
}

function buildListCards(context: ResolvedContext): CourseExerciseCard[] {
    const exercises = sortExercises(visibleExercises(context), context);
    if (exercises.length === 0) return [];
    return [{ id: 'all', title: context.translate('artemisApp.exerciseManagement.card.all'), exercises }];
}

function buildGroupCards(context: ResolvedContext): CourseExerciseCard[] {
    const groupedIds = new Set<number>();
    const searching = hasSearch(context);
    const cards: CourseExerciseCard[] = context.groups
        .slice()
        .sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''))
        .map((group) => {
            const members = group.exercises ?? [];
            members.forEach((exercise) => exercise.id !== undefined && groupedIds.add(exercise.id));
            return {
                id: `group-${group.id}`,
                title: group.title ?? context.translate('artemisApp.exerciseManagement.card.group', { id: group.id }),
                group,
                exercises: sortExercises(
                    members.filter((exercise) => matches(exercise, context)),
                    context,
                ),
            };
        })
        .filter((card) => !searching || card.exercises.length > 0);

    const ungrouped = sortExercises(
        visibleExercises(context).filter((exercise) => exercise.id === undefined || !groupedIds.has(exercise.id)),
        context,
    );
    if (ungrouped.length > 0) {
        cards.push({ id: 'ungrouped', title: context.translate('artemisApp.exerciseManagement.card.ungrouped'), exercises: ungrouped });
    }
    return cards;
}

function buildTypeCards(context: ResolvedContext): CourseExerciseCard[] {
    return TYPE_ORDER.map((type) => ({
        id: `type-${type}`,
        title: TYPE_TITLE_KEYS[type] ? context.translate(TYPE_TITLE_KEYS[type]) : type,
        icon: getIcon(type),
        exerciseType: type,
        exercises: sortExercises(
            visibleExercises(context).filter((exercise) => exercise.type === type),
            context,
        ),
    })).filter((card) => card.exercises.length > 0);
}

function buildWeekCards(context: ResolvedContext): CourseExerciseCard[] {
    const startOf = (exercise: Exercise): dayjs.Dayjs | undefined => {
        const group = owningGroupOf(exercise, context);
        return effectiveDate(exercise, group, 'startDate') ?? effectiveDate(exercise, group, 'releaseDate');
    };

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

    const cards: CourseExerciseCard[] = [...byWeek.keys()]
        .sort((a, b) => a - b)
        .map((weekIndex) => ({
            id: `week-${weekIndex}`,
            title: context.translate('artemisApp.exerciseManagement.card.week', { number: weekIndex + 1 }),
            exercises: sortExercises(byWeek.get(weekIndex)!, context),
        }));

    if (undated.length > 0) {
        cards.push({ id: 'unscheduled', title: context.translate('artemisApp.exerciseManagement.card.unscheduled'), exercises: sortExercises(undated, context) });
    }
    return cards;
}
