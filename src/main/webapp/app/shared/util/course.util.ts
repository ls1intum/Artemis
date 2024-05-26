import { Course } from 'app/entities/course.model';

export function sortCourses(courses: Course[]): Course[] {
    return courses.sort((courseA, courseB) => (courseA.title ?? '').localeCompare(courseB.title ?? ''));
}
