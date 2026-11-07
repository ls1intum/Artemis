package de.tum.cit.aet.artemis.course.domain;

/** Contexts for the deterministic student welcome, in active-course priority order. */
public enum CourseHomeWelcomeCase {
    BEFORE_START, COURSE_ENDED, EXAM_SOON, DEADLINE_TODAY, IMPORTANT_ANNOUNCEMENT, UNREAD_REPLY, FIRST_VISIT, DEADLINE_SOON, NEW_FEEDBACK, RETURNING, IN_PROGRESS, NEW_EXERCISE,
    NEW_LECTURE, NEW_FAQ, CAUGHT_UP
}
