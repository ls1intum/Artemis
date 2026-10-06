/** The roles a user can hold in a course, ordered from the most to the least privileged. */
export const COURSE_ROLES_DESCENDING = ['INSTRUCTOR', 'EDITOR', 'TEACHING_ASSISTANT', 'STUDENT'] as const;

export type CourseRoleName = (typeof COURSE_ROLES_DESCENDING)[number];

/** One role a user holds in one course. A user with several roles in a course has one entry per role. */
export interface UserCourseRole {
    courseId: number;
    courseTitle?: string;
    courseShortName?: string;
    courseSemester?: string;
    role: CourseRoleName;
}
