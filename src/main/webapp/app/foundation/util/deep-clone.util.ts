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
