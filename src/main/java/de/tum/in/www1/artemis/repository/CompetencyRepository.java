package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Competency entity.
 */
@Repository
public interface CompetencyRepository extends JpaRepository<Competency, Long> {

    @Query("""
            SELECT c
            FROM Competency c
            LEFT JOIN FETCH c.userProgress progress
                WHERE c.course.id = :courseId
            """)
    Set<Competency> findAllForCourse(@Param("courseId") Long courseId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.userProgress progress
            WHERE c.course.id = :courseId
                AND (progress IS NULL OR progress.user.id = :userId)
            """)
    Set<Competency> findAllForCourseWithProgressForUser(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.lectureUnits lu
            WHERE c.id = :#{#competencyId}
            """)
    Optional<Competency> findByIdWithLectureUnits(@Param("competencyId") long competencyId);

    /**
     * Fetches a competency with all linked exercises, lecture units, the associated participations, progress, and completion of the specified user.
     * <p>
     * IMPORTANT: We use the entity graph to fetch the lazy loaded data. The fetched data is limited by joining on the user id.
     *
     * @param competencyId the id of the competency that should be fetched
     * @param userId       the id of the user whose progress should be fetched
     * @return the competency
     */
    @Query("""
            SELECT competency
            FROM Competency competency
                LEFT JOIN competency.userProgress progress
                    ON competency.id = progress.learningGoal.id AND progress.user.id = :userId
                LEFT JOIN FETCH competency.exercises exercises
                LEFT JOIN exercises.studentParticipations participations
                    ON participations.student.id = :userId
                LEFT JOIN participations.submissions submissions
                    ON submissions.participation.id = participations.id
                LEFT JOIN submissions.results results
                    ON results.submission.id = submissions.id
                LEFT JOIN FETCH competency.lectureUnits lectureUnits
                LEFT JOIN lectureUnits.completedUsers completedUsers
                    ON lectureUnits.id = completedUsers.lectureUnit.id AND completedUsers.user.id = :userId
                LEFT JOIN FETCH lectureUnits.lecture
            WHERE competency.id = :competencyId
            """)
    @EntityGraph(type = LOAD, attributePaths = { "userProgress", "exercises.studentParticipations", "lectureUnits.completedUsers" })
    Optional<Competency> findByIdWithExercisesAndParticipationsAndLectureUnitsAndProgressForUser(@Param("competencyId") long competencyId, @Param("userId") long userId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH lu.completedUsers
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithLectureUnitsAndCompletions(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.exercises
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH lu.completedUsers
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithExercisesAndLectureUnitsAndCompletions(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.exercises ex
                LEFT JOIN FETCH ex.competencies
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH lu.competencies
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithExercisesAndLectureUnitsBidirectional(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.consecutiveCourses
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithConsecutiveCourses(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT pr
            FROM Competency pr
                LEFT JOIN FETCH pr.consecutiveCourses c
            WHERE c.id = :courseId
            ORDER BY pr.title
            """)
    Set<Competency> findPrerequisitesByCourseId(@Param("courseId") Long courseId);

    /**
     * Query which fetches all competencies for which the user is editor or instructor in the course and
     * matching the search criteria.
     *
     * @param partialTitle       competency title search term
     * @param partialCourseTitle course title search term
     * @param groups             user groups
     * @param pageable           Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT c
            FROM Competency c
            WHERE (c.course.instructorGroupName IN :groups OR c.course.editorGroupName IN :groups)
                AND (c.title LIKE %:partialTitle% OR c.course.title LIKE %:partialCourseTitle%)
            """)
    Page<Competency> findByTitleInLectureOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle, @Param("partialCourseTitle") String partialCourseTitle,
            @Param("groups") Set<String> groups, Pageable pageable);

    /**
     * Returns the title of the competency with the given id.
     *
     * @param competencyId the id of the competency
     * @return the name/title of the competency or null if the competency does not exist
     */
    @Query("""
            SELECT c.title
            FROM Competency c
            WHERE c.id = :competencyId
            """)
    @Cacheable(cacheNames = "competencyTitle", key = "#competencyId", unless = "#result == null")
    String getCompetencyTitle(@Param("competencyId") Long competencyId);

    @SuppressWarnings("PMD.MethodNamingConventions")
    Page<Competency> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(String partialTitle, String partialCourseTitle, Pageable pageable);

    default Competency findByIdWithLectureUnitsAndCompletionsElseThrow(long competencyId) {
        return findByIdWithLectureUnitsAndCompletions(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(long competencyId) {
        return findByIdWithExercisesAndLectureUnitsBidirectional(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithConsecutiveCoursesElseThrow(long competencyId) {
        return findByIdWithConsecutiveCourses(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdElseThrow(Long competencyId) {
        return findById(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithLectureUnitsElseThrow(Long competencyId) {
        return findByIdWithLectureUnits(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithExercisesAndParticipationsAndLectureUnitsAndProgressForUserElseThrow(long competencyId, long userId) {
        return findByIdWithExercisesAndParticipationsAndLectureUnitsAndProgressForUser(competencyId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    long countByCourse(Course course);
}
