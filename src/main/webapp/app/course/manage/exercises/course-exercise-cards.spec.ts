import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { CourseExerciseCardContext, buildCourseExerciseCards, owningGroup } from 'app/course/manage/exercises/course-exercise-cards';

describe('exercise cards', () => {
    const programming = { id: 1, title: 'Sorting in Java', type: ExerciseType.PROGRAMMING, dueDate: dayjs('2026-07-10') } as Exercise;
    const text = { id: 2, title: 'Essay', type: ExerciseType.TEXT, dueDate: dayjs('2026-07-03') } as Exercise;
    const quiz = { id: 3, title: 'Sorting Quiz', type: ExerciseType.QUIZ } as Exercise;
    const group: CourseExerciseGroup = { id: 10, title: 'Sorting variants', exercises: [programming, quiz], dueDate: dayjs('2026-07-05') };

    const context = (overrides?: Partial<CourseExerciseCardContext>): CourseExerciseCardContext => ({
        exercises: [programming, text, quiz],
        groups: [group],
        searchTerm: '',
        translate: (key) => key,
        ...overrides,
    });

    it('builds a single card with all exercises in the list view', () => {
        const cards = buildCourseExerciseCards('list', context());
        expect(cards).toHaveLength(1);
        expect(cards[0].exercises).toHaveLength(3);
    });

    it('returns no cards in the list view when nothing matches', () => {
        expect(buildCourseExerciseCards('list', context({ exercises: [] }))).toHaveLength(0);
    });

    it('builds one card per exercise type with matching exercises only', () => {
        const cards = buildCourseExerciseCards('type', context());
        expect(cards.map((card) => card.exerciseType)).toEqual([ExerciseType.PROGRAMMING, ExerciseType.QUIZ, ExerciseType.TEXT]);
        expect(cards.every((card) => card.exercises.length === 1)).toBe(true);
    });

    it('puts grouped exercises into their group card and the rest into ungrouped', () => {
        const cards = buildCourseExerciseCards('group', context());
        const groupCard = cards.find((card) => card.id === 'group-10');
        const ungrouped = cards.find((card) => card.id === 'ungrouped');
        expect(groupCard?.exercises.map((exercise) => exercise.id)).toEqual(expect.arrayContaining([1, 3]));
        expect(ungrouped?.exercises.map((exercise) => exercise.id)).toEqual([2]);
    });

    it('hides empty group cards while searching', () => {
        const cards = buildCourseExerciseCards('group', context({ searchTerm: 'Essay' }));
        expect(cards.map((card) => card.id)).toEqual(['ungrouped']);
    });

    it('filters by the search term case-insensitively', () => {
        const cards = buildCourseExerciseCards('list', context({ searchTerm: 'sorting' }));
        expect(cards[0].exercises.map((exercise) => exercise.id)).toEqual(expect.arrayContaining([1, 3]));
        expect(cards[0].exercises).toHaveLength(2);
    });

    it('sorts exercises by their effective due date (group dates govern members)', () => {
        const cards = buildCourseExerciseCards('list', context());
        // text is due 07-03, the grouped exercises share the group due date 07-05 (before programming's own 07-10).
        expect(cards[0].exercises[0].id).toBe(2);
    });

    it('sorts exercises without an effective due date last, not first', () => {
        const dated = { id: 20, title: 'Dated', type: ExerciseType.TEXT, dueDate: dayjs('2026-07-09') } as Exercise;
        const undated = { id: 21, title: 'Undated', type: ExerciseType.TEXT } as Exercise;
        const cards = buildCourseExerciseCards('list', context({ exercises: [undated, dated], groups: [] }));
        expect(cards[0].exercises.map((exercise) => exercise.id)).toEqual([20, 21]);
    });

    it('cards dated exercises by week and collects undated ones separately', () => {
        const early = { id: 4, title: 'Week one', type: ExerciseType.TEXT, releaseDate: dayjs('2026-06-01') } as Exercise;
        const later = { id: 5, title: 'Week three', type: ExerciseType.TEXT, releaseDate: dayjs('2026-06-15') } as Exercise;
        const undated = { id: 6, title: 'Sometime', type: ExerciseType.TEXT } as Exercise;
        const cards = buildCourseExerciseCards('week', context({ exercises: [early, later, undated], groups: [] }));
        expect(cards.map((card) => card.id)).toEqual(['week-0', 'week-2', 'unscheduled']);
    });

    it('resolves the owning group of an exercise', () => {
        expect(owningGroup(programming, [group])).toBe(group);
        expect(owningGroup(text, [group])).toBeUndefined();
    });
});
