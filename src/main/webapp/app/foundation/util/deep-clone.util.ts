// eslint-disable-next-line no-restricted-imports -- the single sanctioned cloneDeep import; every other call site goes through the wrappers below
import { cloneDeep as lodashCloneDeep } from 'lodash-es';

/**
 * Creates a deep clone of an object using lodash's cloneDeep.
 *
 * This function correctly handles:
 * - Day.js date objects (preserved as Day.js instances)
 * - Circular references
 * - Map, Set, Date, and other built-in types
 * - Nested objects and arrays
 *
 * Use this instead of spread operator, Object.assign(), or structuredClone()
 * when cloning objects that may contain Day.js dates (e.g., Lecture, Exercise, etc.).
 *
 * @param obj The object to clone
 * @returns A deep clone of the object
 *
 * @example
 * const newLecture = deepClone(this.lecture());
 * newLecture.title = 'New Title';
 * this.lectureChange.emit(newLecture);
 */
export function deepClone<T>(obj: T): T {
    return lodashCloneDeep(obj);
}

/**
 * Deep-clones `source` and applies `overrides` on top — the immutable-update form of `{ ...source, ...overrides }`.
 *
 * `source` is deep-cloned, so the result shares no nested object or array with it. `overrides` values are taken
 * by reference, exactly as object spread does: `cloneWith(option, { selected: signal(false) })` keeps the live
 * signal rather than cloning it into a dead copy.
 *
 * The result is typed `T & U` so a call may add fields the source type does not declare, which object spread also
 * allowed: `cloneWith(exercise, { isAtLeastTutor: true })`.
 *
 * @param source The object to copy
 * @param overrides The properties to set on the copy
 * @returns A deep clone of `source` carrying `overrides`
 *
 * @example
 * this.quizExercise.update((exercise) => cloneWith(exercise, { isVisibleBeforeStart: true }));
 */
export function cloneWith<T extends object, U extends object>(source: T, overrides: U): T & U {
    const clone = deepClone(source) as T & U;
    // Object.keys (rather than Object.assign, which this file's own convention forbids) also keeps keys whose
    // value is explicitly undefined, so `cloneWith(result, { submission: undefined })` clears the field.
    for (const key of Object.keys(overrides)) {
        (clone as Record<string, unknown>)[key] = (overrides as Record<string, unknown>)[key];
    }
    return clone;
}

/**
 * Copies a deep clone of each source's own properties onto `target`, keeping `target`'s prototype.
 *
 * This is the sanctioned way to turn a plain server DTO into a typed model instance, where the point is not to
 * copy an object but to give parsed JSON a prototype so its methods and getters work:
 * `hydrate(new Course(), dto)` replaces `Object.assign(new Course(), dto)`.
 *
 * Unlike `Object.assign`, the copied values are detached from the sources, so later edits to the model cannot
 * reach back into the DTO. Later sources win over earlier ones, as with `Object.assign`.
 *
 * `target` is mutated and returned. Use this only when populating an object you just created, or when mutating
 * in place is the deliberate intent; to derive a modified copy of an existing object use `cloneWith` instead,
 * which leaves the original untouched.
 *
 * @param target The instance to populate, whose prototype is preserved
 * @param sources The DTOs to read from, applied in order
 * @returns `target`, populated
 *
 * @example
 * const course = hydrate(new Course(), courseDto);
 * course.isAtLeastTutor(); // a prototype method, unavailable on the raw DTO
 */
export function hydrate<T extends object, S extends object>(target: T, source: S): T & S;
export function hydrate<T extends object, S1 extends object, S2 extends object>(target: T, source1: S1, source2: S2): T & S1 & S2;
export function hydrate<T extends object, S1 extends object, S2 extends object, S3 extends object>(target: T, source1: S1, source2: S2, source3: S3): T & S1 & S2 & S3;
export function hydrate<T extends object>(target: T, ...sources: object[]): T {
    for (const source of sources) {
        const clone = deepClone(source);
        for (const key of Object.keys(clone)) {
            (target as Record<string, unknown>)[key] = (clone as Record<string, unknown>)[key];
        }
    }
    return target;
}
