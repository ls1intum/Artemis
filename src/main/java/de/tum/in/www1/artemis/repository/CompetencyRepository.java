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
 * Spring Data JPA repository for the Learning Goal entity.
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
            WHERE lg.id = :#{#learningGoalId}
            """)
    Optional<Competency> findByIdWithExercises(@Param("learningGoalId") long learningGoalId);

    @Query("""
            SELECT lg
            FROM Competency lg
                LEFT JOIN FETCH lg.lectureUnits lu
            WHERE lg.id = :#{#learningGoalId}
            """)
    Optional<Competency> findByIdWithLectureUnits(@Param("learningGoalId") long learningGoalId);

    @Query("""
            SELECT lg
            FROM Competency lg
                LEFT JOIN FETCH lg.userProgress
                LEFT JOIN FETCH lg.exercises
                LEFT JOIN FETCH lg.lectureUnits lu
                LEFT JOIN FETCH lu.completedUsers
                LEFT JOIN FETCH lu.lecture l
                LEFT JOIN FETCH lu.exercise e
            WHERE lg.id = :learningGoalId
            """)
    Optional<Competency> findByIdWithExercisesAndLectureUnits(@Param("learningGoalId") Long learningGoalId);

    @Query("""
            SELECT lg
            FROM Competency lg
                LEFT JOIN FETCH lg.lectureUnits lu
                LEFT JOIN FETCH lu.completedUsers
            WHERE lg.id = :learningGoalId
            """)
    Optional<Competency> findByIdWithLectureUnitsAndCompletions(@Param("learningGoalId") Long learningGoalId);

    @Query("""
            SELECT lg
            FROM Competency lg
                LEFT JOIN FETCH lg.exercises
                LEFT JOIN FETCH lg.lectureUnits lu
                LEFT JOIN FETCH lu.completedUsers
            WHERE lg.id = :learningGoalId
            """)
    Optional<Competency> findByIdWithExercisesAndLectureUnitsAndCompletions(@Param("learningGoalId") Long learningGoalId);

    @Query("""
            SELECT lg
            FROM Competency lg
                LEFT JOIN FETCH lg.exercises ex
                LEFT JOIN FETCH ex.competencies
                LEFT JOIN FETCH lg.lectureUnits lu
                LEFT JOIN FETCH lu.competencies
            WHERE lg.id = :learningGoalId
            """)
    Optional<Competency> findByIdWithExercisesAndLectureUnitsBidirectional(@Param("learningGoalId") Long learningGoalId);

    @Query("""
            SELECT lg
            FROM Competency lg
                LEFT JOIN FETCH lg.consecutiveCourses
            WHERE lg.id = :learningGoalId
            """)
    Optional<Competency> findByIdWithConsecutiveCourses(@Param("learningGoalId") Long learningGoalId);

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
     * @param learningGoalId the id of the competency
     * @return the name/title of the competency or null if the competency does not exist
     */
    @Query("""
            SELECT lg.title
            FROM Competency lg
            WHERE lg.id = :learningGoalId
            """)
    @Cacheable(cacheNames = "learningGoalTitle", key = "#learningGoalId", unless = "#result == null")
    String getLearningGoalTitle(@Param("learningGoalId") Long learningGoalId);

    @SuppressWarnings("PMD.MethodNamingConventions")
    Page<Competency> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(String partialTitle, String partialCourseTitle, Pageable pageable);

    default Competency findByIdWithLectureUnitsAndCompletionsElseThrow(long learningGoalId) {
        return findByIdWithLectureUnitsAndCompletions(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default Competency findByIdWithExercisesAndLectureUnitsAndCompletionsElseThrow(long learningGoalId) {
        return findByIdWithExercisesAndLectureUnitsAndCompletions(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default Competency findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(long learningGoalId) {
        return findByIdWithExercisesAndLectureUnitsBidirectional(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default Competency findByIdWithConsecutiveCoursesElseThrow(long learningGoalId) {
        return findByIdWithConsecutiveCourses(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default Competency findByIdElseThrow(Long learningGoalId) {
        return findById(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default Competency findByIdWithLectureUnitsElseThrow(Long learningGoalId) {
        return findByIdWithLectureUnits(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default Competency findByIdWithExercisesAndLectureUnitsElseThrow(Long learningGoalId) {
        return findByIdWithExercisesAndLectureUnits(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default Competency findByIdWithExercisesElseThrow(Long learningGoalId) {
        return findByIdWithExercises(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }
}
