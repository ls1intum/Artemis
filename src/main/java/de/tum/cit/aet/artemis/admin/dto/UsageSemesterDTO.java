package de.tum.cit.aet.artemis.admin.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Aggregated, anonymous usage numbers of one semester. Contains no per-course or per-user data.
 *
 * @param label             human readable semester, e.g. "WS 2025/26" or "SS 2026"
 * @param courses           number of real courses that started in the semester
 * @param students          unique students enrolled in at least one of these courses
 * @param enrollments       student enrolments in these courses, not unique: a student of several courses is counted once per course
 * @param activeStudents    unique students with at least one submission in an exercise of these courses, including their exams
 * @param submissions       number of submissions of students in the exercises of these courses, regardless of the submission date
 * @param lectures          number of lectures in these courses
 * @param exercisesByType   number of exercises in these courses, keyed by exercise type (all types present, possibly 0)
 * @param builds            build numbers of the exercises of these courses, absent for a semester that started before build jobs are deleted
 * @param communication     posts, replies, reactions and published FAQs of these courses
 * @param irisMessages      number of messages users and Iris exchanged in these courses, absent if Iris is not enabled
 * @param tutorialGroups    number of tutorial groups in these courses, absent if tutorial groups are not enabled
 * @param exams             number of real exams in these courses (no test exams and no exams whose title starts with "test" or "mock"), absent if exams are not enabled
 * @param examRegistrations registrations of students for these exams, not unique: a student registered for several exams is counted once per exam, absent if exams are not enabled
 * @param competencies      number of competencies in these courses, without prerequisites, absent if the atlas module is not enabled
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UsageSemesterDTO(String label, long courses, long students, long enrollments, long activeStudents, long submissions, long lectures, Map<String, Long> exercisesByType,
        BuildUsageDTO builds, CommunicationUsageDTO communication, Long irisMessages, Long tutorialGroups, Long exams, Long examRegistrations, Long competencies)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
