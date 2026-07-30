import { describe, expect, it } from 'vitest';
import { cloneWith, deepClone, hydrate } from 'app/foundation/util/deep-clone.util';
import dayjs from 'dayjs/esm';

describe('deepClone', () => {
    it('should return null for null input', () => {
        expect(deepClone(null)).toBeNull();
    });

    it('should return undefined for undefined input', () => {
        expect(deepClone(undefined)).toBeUndefined();
    });

    it('should clone primitive values', () => {
        expect(deepClone('test')).toBe('test');
        expect(deepClone(42)).toBe(42);
        expect(deepClone(true)).toBe(true);
    });

    it('should create a new object reference for plain objects', () => {
        const original = { name: 'Test', value: 123 };
        const cloned = deepClone(original);

        expect(cloned).toEqual(original);
        expect(cloned).not.toBe(original);
    });

    it('should deep clone nested objects', () => {
        const original = {
            outer: {
                inner: {
                    value: 'deep',
                },
            },
        };
        const cloned = deepClone(original);

        expect(cloned).toEqual(original);
        expect(cloned.outer).not.toBe(original.outer);
        expect(cloned.outer.inner).not.toBe(original.outer.inner);
    });

    it('should clone arrays with new references', () => {
        const original = [1, 2, { nested: true }];
        const cloned = deepClone(original);

        expect(cloned).toEqual(original);
        expect(cloned).not.toBe(original);
        expect(cloned[2]).not.toBe(original[2]);
    });

    it('should correctly clone Day.js objects', () => {
        const originalDate = dayjs('2024-01-15T10:30:00');
        const cloned = deepClone(originalDate);

        expect(dayjs.isDayjs(cloned)).toBe(true);
        expect(cloned.isSame(originalDate)).toBe(true);
        expect(cloned).not.toBe(originalDate);
    });

    it('should correctly clone objects containing Day.js properties', () => {
        const original = {
            title: 'Test Lecture',
            startDate: dayjs('2024-01-15T10:00:00'),
            endDate: dayjs('2024-01-15T12:00:00'),
        };
        const cloned = deepClone(original);

        expect(cloned.title).toBe(original.title);
        expect(dayjs.isDayjs(cloned.startDate)).toBe(true);
        expect(dayjs.isDayjs(cloned.endDate)).toBe(true);
        expect(cloned.startDate.isSame(original.startDate)).toBe(true);
        expect(cloned.endDate.isSame(original.endDate)).toBe(true);
        expect(cloned.startDate).not.toBe(original.startDate);
        expect(cloned.endDate).not.toBe(original.endDate);
    });

    it('should handle arrays containing Day.js objects', () => {
        const original = [dayjs('2024-01-01'), dayjs('2024-02-01')];
        const cloned = deepClone(original);

        expect(cloned).toHaveLength(2);
        expect(dayjs.isDayjs(cloned[0])).toBe(true);
        expect(dayjs.isDayjs(cloned[1])).toBe(true);
        expect(cloned[0]).not.toBe(original[0]);
    });

    it('should not mutate the original object when cloned object is modified', () => {
        const original = {
            title: 'Original',
            date: dayjs('2024-01-15'),
        };
        const cloned = deepClone(original);

        cloned.title = 'Modified';

        expect(original.title).toBe('Original');
    });

    it('should handle circular references in objects', () => {
        const original: { name: string; self?: unknown } = { name: 'test' };
        original.self = original;

        const cloned = deepClone(original);

        expect(cloned.name).toBe('test');
        expect(cloned.self).toBe(cloned); // circular reference preserved
        expect(cloned).not.toBe(original);
    });

    it('should handle circular references in arrays', () => {
        const original: unknown[] = [1, 2];
        original.push(original);

        const cloned = deepClone(original);

        expect(cloned[0]).toBe(1);
        expect(cloned[1]).toBe(2);
        expect(cloned[2]).toBe(cloned); // circular reference preserved
        expect(cloned).not.toBe(original);
    });

    it('should handle complex circular references', () => {
        const a: { name: string; ref?: unknown } = { name: 'a' };
        const b: { name: string; ref?: unknown } = { name: 'b' };
        a.ref = b;
        b.ref = a;

        const clonedA = deepClone(a);

        expect(clonedA.name).toBe('a');
        expect((clonedA.ref as typeof b).name).toBe('b');
        expect((clonedA.ref as typeof b).ref).toBe(clonedA);
        expect(clonedA).not.toBe(a);
    });

    it('should clone Map objects with new references', () => {
        const original = new Map<string, { value: number }>();
        original.set('key1', { value: 1 });
        original.set('key2', { value: 2 });

        const cloned = deepClone(original);

        expect(cloned).toBeInstanceOf(Map);
        expect(cloned.size).toBe(2);
        expect(cloned.get('key1')).toEqual({ value: 1 });
        expect(cloned.get('key1')).not.toBe(original.get('key1'));
        expect(cloned).not.toBe(original);
    });

    it('should clone Map with Day.js values', () => {
        const original = new Map<string, ReturnType<typeof dayjs>>();
        original.set('date1', dayjs('2024-01-15'));

        const cloned = deepClone(original);

        expect(cloned).toBeInstanceOf(Map);
        expect(dayjs.isDayjs(cloned.get('date1'))).toBe(true);
        expect(cloned.get('date1')!.isSame(original.get('date1'))).toBe(true);
        expect(cloned.get('date1')).not.toBe(original.get('date1'));
    });

    it('should clone Set objects with new references', () => {
        const obj1 = { id: 1 };
        const obj2 = { id: 2 };
        const original = new Set([obj1, obj2]);

        const cloned = deepClone(original);

        expect(cloned).toBeInstanceOf(Set);
        expect(cloned.size).toBe(2);
        expect(cloned).not.toBe(original);
        // Objects in the set should be cloned
        const clonedArray = Array.from(cloned);
        expect(clonedArray[0]).not.toBe(obj1);
        expect(clonedArray[0]).toEqual(obj1);
    });

    it('should clone Set with Day.js values', () => {
        const date1 = dayjs('2024-01-15');
        const date2 = dayjs('2024-02-20');
        const original = new Set([date1, date2]);

        const cloned = deepClone(original);

        expect(cloned).toBeInstanceOf(Set);
        expect(cloned.size).toBe(2);
        const clonedArray = Array.from(cloned);
        expect(dayjs.isDayjs(clonedArray[0])).toBe(true);
        expect(dayjs.isDayjs(clonedArray[1])).toBe(true);
    });

    it('should clone native Date objects', () => {
        const original = new Date('2024-01-15T10:30:00');

        const cloned = deepClone(original);

        expect(cloned).toBeInstanceOf(Date);
        expect(cloned.getTime()).toBe(original.getTime());
        expect(cloned).not.toBe(original);
    });

    it('should clone objects containing native Date properties', () => {
        const original = {
            title: 'Event',
            createdAt: new Date('2024-01-15'),
        };

        const cloned = deepClone(original);

        expect(cloned.createdAt).toBeInstanceOf(Date);
        expect(cloned.createdAt.getTime()).toBe(original.createdAt.getTime());
        expect(cloned.createdAt).not.toBe(original.createdAt);
    });

    it('should clone TypeScript Record types', () => {
        const original: Record<string, { count: number }> = {
            item1: { count: 10 },
            item2: { count: 20 },
        };

        const cloned = deepClone(original);

        expect(cloned).toEqual(original);
        expect(cloned).not.toBe(original);
        expect(cloned.item1).not.toBe(original.item1);
    });

    it('should handle circular references in Map', () => {
        const original = new Map<string, unknown>();
        original.set('self', original);

        const cloned = deepClone(original);

        expect(cloned).toBeInstanceOf(Map);
        expect(cloned.get('self')).toBe(cloned);
        expect(cloned).not.toBe(original);
    });

    it('should handle circular references in Set', () => {
        const original = new Set<unknown>();
        original.add(original);

        const cloned = deepClone(original);

        expect(cloned).toBeInstanceOf(Set);
        expect(cloned.has(cloned)).toBe(true);
        expect(cloned).not.toBe(original);
    });
});

describe('cloneWith', () => {
    it('should return a new object carrying the overrides', () => {
        const original = { title: 'Original', points: 10 };

        const updated = cloneWith(original, { title: 'Updated' });

        expect(updated).toEqual({ title: 'Updated', points: 10 });
        expect(updated).not.toBe(original);
    });

    it('should not mutate the source', () => {
        const original = { title: 'Original', nested: { value: 1 } };

        const updated = cloneWith(original, { title: 'Updated' });
        updated.nested.value = 2;

        expect(original.title).toBe('Original');
        expect(original.nested.value).toBe(1);
    });

    it('should detach nested objects and arrays from the source', () => {
        const original = { course: { id: 1 }, tags: ['a'] };

        const updated = cloneWith(original, { tags: ['a', 'b'] });

        expect(updated.course).not.toBe(original.course);
        expect(updated.tags).toEqual(['a', 'b']);
    });

    it('should preserve Day.js properties that are not overridden', () => {
        const original = { title: 'Lecture', startDate: dayjs('2024-01-15T10:00:00') };

        const updated = cloneWith(original, { title: 'Renamed' });

        expect(dayjs.isDayjs(updated.startDate)).toBe(true);
        expect(updated.startDate.isSame(original.startDate)).toBe(true);
        expect(updated.startDate).not.toBe(original.startDate);
    });

    it('should apply an explicitly undefined override rather than skipping it', () => {
        const original = { id: 1, submission: { id: 5 } as { id: number } | undefined };

        const updated = cloneWith(original, { submission: undefined });

        expect(updated.submission).toBeUndefined();
        expect('submission' in updated).toBe(true);
    });

    it('should take override values by reference so live values survive', () => {
        const marker = () => 'live';
        const original = { id: 1 };

        const updated = cloneWith(original, { callback: marker });

        // A deep clone of a function would yield an unusable copy; overrides must pass through untouched.
        expect(updated.callback).toBe(marker);
    });

    it('should add fields the source type does not declare', () => {
        const original: { id: number } = { id: 1 };

        const updated = cloneWith(original, { isAtLeastTutor: true });

        expect(updated.id).toBe(1);
        expect(updated.isAtLeastTutor).toBe(true);
    });

    it('should support computed override keys', () => {
        const original: Record<string, boolean> = { a: true };
        const key = 'b';

        const updated = cloneWith(original, { [key]: false });

        expect(updated).toEqual({ a: true, b: false });
    });

    it('should let the overrides win over the source', () => {
        const updated = cloneWith({ value: 'source' }, { value: 'override' });

        expect(updated.value).toBe('override');
    });
});

describe('hydrate', () => {
    class Lecture {
        title?: string;
        startDate?: ReturnType<typeof dayjs>;

        displayTitle(): string {
            return `Lecture: ${this.title}`;
        }
    }

    it('should populate the instance and keep its prototype methods', () => {
        const dto = { title: 'Intro' };

        const lecture = hydrate(new Lecture(), dto);

        expect(lecture).toBeInstanceOf(Lecture);
        expect(lecture.title).toBe('Intro');
        expect(lecture.displayTitle()).toBe('Lecture: Intro');
    });

    it('should return the same instance that was passed in', () => {
        const target = new Lecture();

        expect(hydrate(target, { title: 'Intro' })).toBe(target);
    });

    it('should detach the copied values from the source DTO', () => {
        const dto = { nested: { value: 1 } };

        const hydrated = hydrate({} as { nested: { value: number } }, dto);
        hydrated.nested.value = 2;

        expect(dto.nested.value).toBe(1);
    });

    it('should preserve Day.js properties from the DTO', () => {
        const dto = { startDate: dayjs('2024-01-15T10:00:00') };

        const lecture = hydrate(new Lecture(), dto);

        expect(dayjs.isDayjs(lecture.startDate)).toBe(true);
        expect(lecture.startDate!.isSame(dto.startDate)).toBe(true);
        expect(lecture.startDate).not.toBe(dto.startDate);
    });

    it('should keep target properties the source does not mention', () => {
        const target = new Lecture();
        target.title = 'Existing';

        const hydrated = hydrate(target, { startDate: dayjs('2024-01-15') });

        expect(hydrated.title).toBe('Existing');
    });

    it('should apply several sources in order, with later ones winning', () => {
        const hydrated = hydrate(new Lecture(), { title: 'First' }, { title: 'Second' });

        expect(hydrated.title).toBe('Second');
        expect(hydrated).toBeInstanceOf(Lecture);
    });

    it('should merge disjoint properties from several sources', () => {
        const hydrated = hydrate(new Lecture(), { title: 'Intro' }, { startDate: dayjs('2024-01-15') });

        expect(hydrated.title).toBe('Intro');
        expect(dayjs.isDayjs(hydrated.startDate)).toBe(true);
    });
});
