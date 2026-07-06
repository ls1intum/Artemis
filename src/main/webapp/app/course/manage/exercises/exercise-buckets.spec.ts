import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { BucketContext, buildBuckets, owningGroup } from 'app/course/manage/exercises/exercise-buckets';

describe('exercise buckets', () => {
    const programming = { id: 1, title: 'Sorting in Java', type: ExerciseType.PROGRAMMING, dueDate: dayjs('2026-07-10') } as Exercise;
    const text = { id: 2, title: 'Essay', type: ExerciseType.TEXT, dueDate: dayjs('2026-07-03') } as Exercise;
    const quiz = { id: 3, title: 'Sorting Quiz', type: ExerciseType.QUIZ } as Exercise;
    const group: CourseExerciseGroup = { id: 10, title: 'Sorting variants', exercises: [programming, quiz], dueDate: dayjs('2026-07-05') };

    const context = (overrides?: Partial<BucketContext>): BucketContext => ({
        exercises: [programming, text, quiz],
        groups: [group],
        searchTerm: '',
        translate: (key) => key,
        ...overrides,
    });

    it('builds a single bucket with all exercises in the list view', () => {
        const buckets = buildBuckets('list', context());
        expect(buckets).toHaveLength(1);
        expect(buckets[0].exercises).toHaveLength(3);
    });

    it('returns no buckets in the list view when nothing matches', () => {
        expect(buildBuckets('list', context({ exercises: [] }))).toHaveLength(0);
    });

    it('builds one bucket per exercise type with matching exercises only', () => {
        const buckets = buildBuckets('type', context());
        expect(buckets.map((bucket) => bucket.exerciseType)).toEqual([ExerciseType.PROGRAMMING, ExerciseType.QUIZ, ExerciseType.TEXT]);
        expect(buckets.every((bucket) => bucket.exercises.length === 1)).toBe(true);
    });

    it('puts grouped exercises into their group bucket and the rest into ungrouped', () => {
        const buckets = buildBuckets('group', context());
        const groupBucket = buckets.find((bucket) => bucket.id === 'group-10');
        const ungrouped = buckets.find((bucket) => bucket.id === 'ungrouped');
        expect(groupBucket?.exercises.map((exercise) => exercise.id)).toEqual(expect.arrayContaining([1, 3]));
        expect(ungrouped?.exercises.map((exercise) => exercise.id)).toEqual([2]);
    });

    it('hides empty group buckets while searching', () => {
        const buckets = buildBuckets('group', context({ searchTerm: 'Essay' }));
        expect(buckets.map((bucket) => bucket.id)).toEqual(['ungrouped']);
    });

    it('filters by the search term case-insensitively', () => {
        const buckets = buildBuckets('list', context({ searchTerm: 'sorting' }));
        expect(buckets[0].exercises.map((exercise) => exercise.id)).toEqual(expect.arrayContaining([1, 3]));
        expect(buckets[0].exercises).toHaveLength(2);
    });

    it('sorts exercises by their effective due date (group dates govern members)', () => {
        const buckets = buildBuckets('list', context());
        // text is due 07-03, the grouped exercises share the group due date 07-05 (before programming's own 07-10).
        expect(buckets[0].exercises[0].id).toBe(2);
    });

    it('buckets dated exercises by week and collects undated ones separately', () => {
        const early = { id: 4, title: 'Week one', type: ExerciseType.TEXT, releaseDate: dayjs('2026-06-01') } as Exercise;
        const later = { id: 5, title: 'Week three', type: ExerciseType.TEXT, releaseDate: dayjs('2026-06-15') } as Exercise;
        const undated = { id: 6, title: 'Sometime', type: ExerciseType.TEXT } as Exercise;
        const buckets = buildBuckets('week', context({ exercises: [early, later, undated], groups: [] }));
        expect(buckets.map((bucket) => bucket.id)).toEqual(['week-0', 'week-2', 'unscheduled']);
    });

    it('resolves the owning group of an exercise', () => {
        expect(owningGroup(programming, [group])).toBe(group);
        expect(owningGroup(text, [group])).toBeUndefined();
    });
});
