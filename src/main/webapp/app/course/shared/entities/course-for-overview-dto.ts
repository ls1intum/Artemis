import { Course } from 'app/course/shared/entities/course.model';

/**
 * The course data the course overview container needs, returned by `GET api/course/courses/{courseId}/for-overview`.
 *
 * Carries no exercises, lectures, exams, participations or scores — each tab loads what it needs, and which tabs to
 * offer comes from the available-tabs endpoint.
 *
 * Instantiated and/or deserialized from server data; fields are populated after construction, hence the
 * definite-assignment (!) markers.
 */
export class CourseForOverviewDTO {
    course!: Course;
    courseNotificationCount!: number;
}
