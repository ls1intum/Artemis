package de.tum.in.www1.artemis.domain.enumeration;

/**
 * Enum that decides
 * - if and how max_points of the exercise increase the reachable points in a course/exam
 * - if and how points / bonus points achieved by a student in an exercise count towards his/her course / exam score
 */
public enum IncludedInOverallScore {
    /**
     * - exercise can have max points > 0 and max bonus points >= 0
     * - max_points of exercise increase the maximum reachable points in a course / exam
     * - max_bonus_points of exercise do not increase the maximum reachable points in a course / exam
     * - points and bonus points achieved by a student/team do count towards the student's/team's overall course / exam score
     *
     */
    INCLUDED_COMPLETELY,
    /**
     * - exercise can have max points > 0 and max bonus points must be 0
     * - max_points of exercise do NOT increase the maximum reachable points in a course / exam (as they are included as a bonus)
     * - points achieved by a student/team count as bonus points towards the student's/team's overall course / exam score
     */
    INCLUDED_AS_BONUS,
    /**
     * - exercise can have max points > 0 and max bonus points must be 0
     * - max_points of exercise do NOT increase the maximum reachable points in a course / exam (as they are not included at all)
     * - points achieved by a student/team do not count towards the student's/team's overall course / exam score
     */
    NOT_INCLUDED
}
