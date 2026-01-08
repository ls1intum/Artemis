import { deepClone } from 'app/shared/util/deep-clone.util';
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
        expect(deepClone(true)).toBeTrue();
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

        expect(dayjs.isDayjs(cloned)).toBeTrue();
        expect(cloned.isSame(originalDate)).toBeTrue();
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
        expect(dayjs.isDayjs(cloned.startDate)).toBeTrue();
        expect(dayjs.isDayjs(cloned.endDate)).toBeTrue();
        expect(cloned.startDate.isSame(original.startDate)).toBeTrue();
        expect(cloned.endDate.isSame(original.endDate)).toBeTrue();
        expect(cloned.startDate).not.toBe(original.startDate);
        expect(cloned.endDate).not.toBe(original.endDate);
    });

    it('should handle arrays containing Day.js objects', () => {
        const original = [dayjs('2024-01-01'), dayjs('2024-02-01')];
        const cloned = deepClone(original);

        expect(cloned).toHaveLength(2);
        expect(dayjs.isDayjs(cloned[0])).toBeTrue();
        expect(dayjs.isDayjs(cloned[1])).toBeTrue();
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
        expect(dayjs.isDayjs(cloned.get('date1'))).toBeTrue();
        expect(cloned.get('date1')!.isSame(original.get('date1'))).toBeTrue();
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
        expect(dayjs.isDayjs(clonedArray[0])).toBeTrue();
        expect(dayjs.isDayjs(clonedArray[1])).toBeTrue();
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
        expect(cloned.has(cloned)).toBeTrue();
        expect(cloned).not.toBe(original);
    });
});
