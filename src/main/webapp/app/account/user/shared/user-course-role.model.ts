import { CourseRoleSlug } from 'app/course/shared/entities/course.model';

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

/** The URL path segment the course membership endpoints use for every role. */
export const COURSE_ROLE_SLUGS: Record<CourseRoleName, CourseRoleSlug> = {
    INSTRUCTOR: CourseRoleSlug.INSTRUCTORS,
    EDITOR: CourseRoleSlug.EDITORS,
    TEACHING_ASSISTANT: CourseRoleSlug.TUTORS,
    STUDENT: CourseRoleSlug.STUDENTS,
};

/** A course an administrator can choose when assigning a course role. */
export interface CourseForRoleAssignment {
    id: number;
    title?: string;
    shortName?: string;
    semester?: string;
}

const ROLE_TRANSLATION_KEYS: Record<CourseRoleName, string> = {
    INSTRUCTOR: 'instructor',
    EDITOR: 'editor',
    TEACHING_ASSISTANT: 'tutor',
    STUDENT: 'student',
};

/** Translation key of the name of a role in the singular, e.g. "Tutor". */
export const courseRoleTranslationKey = (role: CourseRoleName): string => `artemisApp.userManagement.courseRoles.role.${ROLE_TRANSLATION_KEYS[role]}`;

/** Translation key of the name of a role in the plural, e.g. "Tutors". */
export const courseRolePluralTranslationKey = (role: CourseRoleName): string => `artemisApp.userManagement.courseRoles.roles.${ROLE_TRANSLATION_KEYS[role]}`;

/**
 * Names a course so that courses with the same title can be told apart: the title with the short name in parentheses.
 * Falls back to the short name and then to the id when the title is missing.
 */
export const courseDisplayName = (course: { id: number; title?: string; shortName?: string }): string => {
    if (course.title && course.shortName) {
        return `${course.title} (${course.shortName})`;
    }
    return course.title || course.shortName || String(course.id);
};
