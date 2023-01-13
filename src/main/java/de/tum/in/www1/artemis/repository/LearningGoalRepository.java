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

import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Learning Goal entity.
 */
@Repository
public interface LearningGoalRepository extends JpaRepository<LearningGoal, Long> {

    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            LEFT JOIN FETCH learningGoal.userProgress progress
            WHERE learningGoal.course.id = :courseId
            """)
    Set<LearningGoal> findAllForCourse(@Param("courseId") Long courseId);

    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            LEFT JOIN FETCH learningGoal.userProgress progress
            WHERE learningGoal.course.id = :courseId
            AND progress.user.id = :userId
            """)
    Set<LearningGoal> findAllForCourseWithProgressForUser(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            LEFT JOIN FETCH learningGoal.exercises ex
            WHERE learningGoal.id = :#{#learningGoalId}
            """)
    Optional<LearningGoal> findByIdWithExercises(@Param("learningGoalId") long learningGoalId);

    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            LEFT JOIN FETCH learningGoal.lectureUnits lu
            WHERE learningGoal.id = :#{#learningGoalId}
            """)
    Optional<LearningGoal> findByIdWithLectureUnits(@Param("learningGoalId") long learningGoalId);

    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            LEFT JOIN FETCH learningGoal.userProgress progress
            LEFT JOIN FETCH learningGoal.exercises exercises
            LEFT JOIN FETCH learningGoal.lectureUnits lectureUnits
            LEFT JOIN FETCH lectureUnits.completedUsers completions
            LEFT JOIN FETCH lectureUnits.lecture lecture
            LEFT JOIN FETCH lectureUnits.exercise exercise
            WHERE learningGoal.id = :learningGoalId
            """)
    Optional<LearningGoal> findByIdWithExercisesAndLectureUnits(@Param("learningGoalId") Long learningGoalId);

    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            LEFT JOIN FETCH learningGoal.lectureUnits lu
            LEFT JOIN FETCH lu.completedUsers
            WHERE learningGoal.id = :learningGoalId
            """)
    Optional<LearningGoal> findByIdWithLectureUnitsAndCompletions(@Param("learningGoalId") Long learningGoalId);

    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            LEFT JOIN FETCH learningGoal.exercises
            LEFT JOIN FETCH learningGoal.lectureUnits lu
            LEFT JOIN FETCH lu.completedUsers
            WHERE learningGoal.id = :learningGoalId
            """)
    Optional<LearningGoal> findByIdWithExercisesAndLectureUnitsAndCompletions(@Param("learningGoalId") Long learningGoalId);

    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            LEFT JOIN FETCH learningGoal.exercises exercises
            LEFT JOIN FETCH exercises.learningGoals
            LEFT JOIN FETCH learningGoal.lectureUnits lectureUnits
            LEFT JOIN FETCH lectureUnits.learningGoals
            WHERE learningGoal.id = :learningGoalId
            """)
    Optional<LearningGoal> findByIdWithExercisesAndLectureUnitsBidirectional(@Param("learningGoalId") Long learningGoalId);

    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            LEFT JOIN FETCH learningGoal.consecutiveCourses courses
            WHERE learningGoal.id = :learningGoalId
            """)
    Optional<LearningGoal> findByIdWithConsecutiveCourses(@Param("learningGoalId") Long learningGoalId);

    @Query("""
            SELECT prerequisite
            FROM LearningGoal prerequisite
            LEFT JOIN FETCH prerequisite.consecutiveCourses courses
            WHERE courses.id = :courseId
            ORDER BY prerequisite.title
            """)
    Set<LearningGoal> findPrerequisitesByCourseId(@Param("courseId") Long courseId);

    /**
     * Query which fetches all learning gaols for which the user is editor or instructor in the course and
     * matching the search criteria.
     *
     * @param partialTitle       learning gaol title search term
     * @param partialCourseTitle course title search term
     * @param groups             user groups
     * @param pageable           Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            WHERE (learningGoal.course.instructorGroupName IN :groups OR learningGoal.course.editorGroupName IN :groups)
            AND (learningGoal.title LIKE %:partialTitle% OR learningGoal.course.title LIKE %:partialCourseTitle%)
            """)
    Page<LearningGoal> findByTitleInLectureOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle, @Param("partialCourseTitle") String partialCourseTitle,
            @Param("groups") Set<String> groups, Pageable pageable);

    /**
     * Returns the title of the learning goal with the given id.
     *
     * @param learningGoalId the id of the learning goal
     * @return the name/title of the learning goal or null if the learning goal does not exist
     */
    @Query("""
            SELECT learningGoal.title
            FROM LearningGoal learningGoal
            WHERE learningGoal.id = :learningGoalId
            """)
    @Cacheable(cacheNames = "learningGoalTitle", key = "#learningGoalId", unless = "#result == null")
    String getLearningGoalTitle(@Param("learningGoalId") Long learningGoalId);

    @SuppressWarnings("PMD.MethodNamingConventions")
    Page<LearningGoal> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(String partialTitle, String partialCourseTitle, Pageable pageable);

    default LearningGoal findByIdWithLectureUnitsAndCompletionsElseThrow(long learningGoalId) {
        return findByIdWithLectureUnitsAndCompletions(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default LearningGoal findByIdWithExercisesAndLectureUnitsAndCompletionsElseThrow(long learningGoalId) {
        return findByIdWithExercisesAndLectureUnitsAndCompletions(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default LearningGoal findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(long learningGoalId) {
        return findByIdWithExercisesAndLectureUnitsBidirectional(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default LearningGoal findByIdWithConsecutiveCoursesElseThrow(long learningGoalId) {
        return findByIdWithConsecutiveCourses(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default LearningGoal findByIdElseThrow(Long learningGoalId) {
        return findById(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default LearningGoal findByIdWithLectureUnitsElseThrow(Long learningGoalId) {
        return findByIdWithLectureUnits(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default LearningGoal findByIdWithExercisesAndLectureUnitsElseThrow(Long learningGoalId) {
        return findByIdWithExercisesAndLectureUnits(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default LearningGoal findByIdWithExercisesElseThrow(Long learningGoalId) {
        return findByIdWithExercises(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }
}
