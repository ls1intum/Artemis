package de.tum.cit.aet.artemis.admin.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.admin.dto.StudentSubmissionCountDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry;

/**
 * Aggregating queries for the public usage dashboard. Every query returns counts or ids, never data that could be published about a person.
 * All queries except {@link #findRealCourseIds} are restricted to the ids of "real" courses or of their exercises. The exercises of a course are
 * the exercises of the course itself and the exercises of its exams, which only reach the course through their exercise group.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PublicUsageStatisticsRepository extends ArtemisJpaRepository<Course, Long> {

    /**
     * Real courses are no test courses and have at least {@code minStudents} students who are neither deleted nor test users.
     * The threshold keeps tiny courses out of every published number.
     *
     * @param start       inclusive start of the semester
     * @param end         exclusive end of the semester
     * @param minStudents minimum number of students
     * @return the ids of the real courses that started in the semester
     */
    @Query("""
            SELECT c.id
            FROM Course c
                JOIN c.courseRoles ucr ON ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.STUDENT
                    AND ucr.user.deleted = FALSE
                    AND ucr.user.isTestUser = FALSE
            WHERE c.startDate >= :start
                AND c.startDate < :end
                AND c.testCourse = FALSE
            GROUP BY c.id
            HAVING COUNT(DISTINCT ucr.user.id) >= :minStudents
            """)
    Set<Long> findRealCourseIds(@Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end, @Param("minStudents") long minStudents);

    /**
     * @param courseIds the real courses
     * @return the ids of the users who are students in at least one of the courses, without deleted users and test users
     */
    @Query("""
            SELECT DISTINCT ucr.user.id
            FROM UserCourseRole ucr
            WHERE ucr.course.id IN :courseIds
                AND ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.STUDENT
                AND ucr.user.deleted = FALSE
                AND ucr.user.isTestUser = FALSE
            """)
    Set<Long> findStudentIds(@Param("courseIds") Set<Long> courseIds);

    /**
     * @param courseIds the real courses
     * @return the ids of all exercises of the courses, including the exercises of their exams
     */
    @Query("""
            SELECT e.id
            FROM Exercise e
                LEFT JOIN e.exerciseGroup eg
                LEFT JOIN eg.exam ex
            WHERE e.course.id IN :courseIds
                OR ex.course.id IN :courseIds
            """)
    Set<Long> findExerciseIdsOfCourses(@Param("courseIds") Set<Long> courseIds);

    /**
     * The submissions per student in the given exercises, over the whole life of the exercises. Submissions that instructors or the system create on
     * behalf of a student do not count. Users are not joined here on purpose: joining the user table over the submissions made the database examine
     * whole submission histories, so the caller filters the students in memory.
     *
     * @param exerciseIds the exercises of the real courses
     * @return one entry per student with at least one submission, plus one entry without student id for team submissions
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.StudentSubmissionCountDTO(p.student.id, COUNT(s.id))
            FROM Submission s
                JOIN StudentParticipation p ON p.id = s.participation.id
            WHERE p.exercise.id IN :exerciseIds
                AND (s.type IS NULL OR s.type NOT IN (
                    de.tum.cit.aet.artemis.exercise.domain.SubmissionType.INSTRUCTOR,
                    de.tum.cit.aet.artemis.exercise.domain.SubmissionType.TEST))
            GROUP BY p.student.id
            """)
    List<StudentSubmissionCountDTO> countSubmissionsByStudent(@Param("exerciseIds") Set<Long> exerciseIds);

    @Query("""
            SELECT COUNT(l.id)
            FROM Lecture l
            WHERE l.course.id IN :courseIds
                AND l.isTutorialLecture = FALSE
            """)
    long countLectures(@Param("courseIds") Set<Long> courseIds);

    /**
     * @param courseIds the real courses
     * @return the number of exercises per type including exam exercises, types without exercises are missing
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry(TYPE(e), COUNT(e.id))
            FROM Exercise e
                LEFT JOIN e.exerciseGroup eg
                LEFT JOIN eg.exam ex
            WHERE e.course.id IN :courseIds
                OR ex.course.id IN :courseIds
            GROUP BY TYPE(e)
            """)
    Set<ExerciseTypeMetricsEntry> countExercisesByType(@Param("courseIds") Set<Long> courseIds);
}
