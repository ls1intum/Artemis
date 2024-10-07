import { Course } from 'app/entities/course.model';

/**
 * Sorts an array of Course objects alphabetically by their title.
 *
 * @param {Course[]} courses - The array of Course objects to be sorted.
 * @param {boolean} isSortAscending - Sort direction, it is ascending by default.
 * @returns {Course[]} The sorted array of Course objects.
 */
export function sortCourses(courses: Course[], isSortAscending: boolean = true): Course[] {
    return courses.sort((courseA, courseB) => {
        const sortOrder = (courseA.title ?? '').localeCompare(courseB.title ?? '');
        return isSortAscending ? sortOrder : -sortOrder;
    });
}
