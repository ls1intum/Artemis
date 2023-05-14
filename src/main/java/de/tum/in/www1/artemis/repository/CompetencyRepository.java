package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Competency;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Competency entity.
 */
@Repository
public interface CompetencyRepository extends JpaRepository<Competency, Long> {

    @Query("""
            SELECT lg
            FROM Competency lg
            LEFT JOIN FETCH lg.userProgress progress
                WHERE lg.course.id = :courseId
            """)
    Set<Competency> findAllForCourse(@Param("courseId") Long courseId);

    @Query("""
            SELECT lg
            FROM Competency lg
                LEFT JOIN FETCH lg.userProgress progress
            WHERE lg.course.id = :courseId
                AND (progress IS NULL OR progress.user.id = :userId)
            """)
    Set<Competency> findAllForCourseWithProgressForUser(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
            SELECT lg
            FROM Competency lg
                LEFT JOIN FETCH lg.exercises ex
            WHERE lg.id = :#{#competencyId}
            """)
    Optional<Competency> findByIdWithExercises(@Param("competencyId") long competencyId);

    @Query("""
            SELECT lg
            FROM Competency lg
                LEFT JOIN FETCH lg.lectureUnits lu
            WHERE lg.id = :#{#competencyId}
            """)
    Optional<Competency> findByIdWithLectureUnits(@Param("competencyId") long competencyId);

    @Query("""
            SELECT lg
            FROM Competency lg
                LEFT JOIN FETCH lg.userProgress
                LEFT JOIN FETCH lg.exercises
                LEFT JOIN FETCH lg.lectureUnits lu
                LEFT JOIN FETCH lu.completedUsers
                LEFT JOIN FETCH lu.lecture l
                LEFT JOIN FETCH lu.exercise e
            WHERE lg.id = :competencyId
            """)
    Optional<Competency> findByIdWithExercisesAndLectureUnits(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT lg
            FROM Competency lg
                LEFT JOIN FETCH lg.lectureUnits lu
                LEFT JOIN FETCH lu.completedUsers
            WHERE lg.id = :competencyId
            """)
    Optional<Competency> findByIdWithLectureUnitsAndCompletions(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT lg
            FROM Competency lg
                LEFT JOIN FETCH lg.exercises
                LEFT JOIN FETCH lg.lectureUnits lu
                LEFT JOIN FETCH lu.completedUsers
            WHERE lg.id = :competencyId
            """)
    Optional<Competency> findByIdWithExercisesAndLectureUnitsAndCompletions(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT lg
            FROM Competency lg
                LEFT JOIN FETCH lg.exercises ex
                LEFT JOIN FETCH ex.competencies
                LEFT JOIN FETCH lg.lectureUnits lu
                LEFT JOIN FETCH lu.competencies
            WHERE lg.id = :competencyId
            """)
    Optional<Competency> findByIdWithExercisesAndLectureUnitsBidirectional(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT lg
            FROM Competency lg
                LEFT JOIN FETCH lg.consecutiveCourses
            WHERE lg.id = :competencyId
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
            SELECT lg
            FROM Competency lg
            WHERE (lg.course.instructorGroupName IN :groups OR lg.course.editorGroupName IN :groups)
                AND (lg.title LIKE %:partialTitle% OR lg.course.title LIKE %:partialCourseTitle%)
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
            SELECT lg.title
            FROM Competency lg
            WHERE lg.id = :competencyId
            """)
    @Cacheable(cacheNames = "learningGoalTitle", key = "#competencyId", unless = "#result == null")
    String getLearningGoalTitle(@Param("competencyId") Long competencyId);

    @SuppressWarnings("PMD.MethodNamingConventions")
    Page<Competency> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(String partialTitle, String partialCourseTitle, Pageable pageable);

    default Competency findByIdWithLectureUnitsAndCompletionsElseThrow(long competencyId) {
        return findByIdWithLectureUnitsAndCompletions(competencyId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", competencyId));
    }

    default Competency findByIdWithExercisesAndLectureUnitsAndCompletionsElseThrow(long competencyId) {
        return findByIdWithExercisesAndLectureUnitsAndCompletions(competencyId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", competencyId));
    }

    default Competency findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(long competencyId) {
        return findByIdWithExercisesAndLectureUnitsBidirectional(competencyId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", competencyId));
    }

    default Competency findByIdWithConsecutiveCoursesElseThrow(long competencyId) {
        return findByIdWithConsecutiveCourses(competencyId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", competencyId));
    }

    default Competency findByIdElseThrow(Long competencyId) {
        return findById(competencyId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", competencyId));
    }

    default Competency findByIdWithLectureUnitsElseThrow(Long competencyId) {
        return findByIdWithLectureUnits(competencyId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", competencyId));
    }

    default Competency findByIdWithExercisesAndLectureUnitsElseThrow(Long competencyId) {
        return findByIdWithExercisesAndLectureUnits(competencyId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", competencyId));
    }

    default Competency findByIdWithExercisesElseThrow(Long competencyId) {
        return findByIdWithExercises(competencyId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", competencyId));
    }
}
