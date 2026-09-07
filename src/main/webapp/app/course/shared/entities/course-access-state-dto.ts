/**
 * Whether the current user already has access to a course, returned by
 * `GET api/course/courses/{courseId}/access-state`.
 *
 * The enrollment page uses this to decide between the enrollment form and a redirect into the course.
 */
export interface CourseAccessStateDTO {
    hasAccess: boolean;
}
