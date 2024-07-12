import { Course } from 'app/entities/course.model';

/**
 * Sorts an array of Course objects alphabetically by their title.
 *
 * @param {Course[]} courses - The array of Course objects to be sorted.
 * @returns {Course[]} The sorted array of Course objects.
 */
export function sortCourses(courses: Course[]): Course[] {
    return courses.sort((courseA, courseB) => (courseA.title ?? '').localeCompare(courseB.title ?? ''));
}
