package de.tum.cit.aet.artemis.admin.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Aggregated, anonymous usage numbers of one semester. Contains no per-course or per-user data.
 *
 * @param label           human readable semester, e.g. "WS 2025/26" or "SS 2026"
 * @param courses         number of real courses that started in the semester
 * @param students        unique students enrolled in at least one of these courses
 * @param activeStudents  unique students with at least one submission in an exercise of these courses, including their exams
 * @param submissions     number of submissions of students in the exercises of these courses, regardless of the submission date
 * @param lectures        number of lectures in these courses
 * @param exercisesByType number of exercises in these courses, keyed by exercise type (all types present, possibly 0)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UsageSemesterDTO(String label, long courses, long students, long activeStudents, long submissions, long lectures, Map<String, Long> exercisesByType)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
