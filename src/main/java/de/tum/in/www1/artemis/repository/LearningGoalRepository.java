package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

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
            LEFT JOIN FETCH learningGoal.lectureUnits lu
            WHERE learningGoal.course.id = :#{#courseId}
            ORDER BY learningGoal.title""")
    Set<LearningGoal> findAllByCourseIdWithLectureUnitsUnidirectional(@Param("courseId") Long courseId);

    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            LEFT JOIN FETCH learningGoal.lectureUnits lu
            LEFT JOIN FETCH lu.learningGoals
            WHERE learningGoal.id = :#{#learningGoalId}
            """)
    Optional<LearningGoal> findByIdWithLectureUnitsBidirectional(@Param("learningGoalId") long learningGoalId);

    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            LEFT JOIN FETCH learningGoal.lectureUnits lu
            LEFT JOIN FETCH lu.completedUsers
            WHERE learningGoal.id = :#{#learningGoalId}
            """)
    Optional<LearningGoal> findByIdWithLectureUnitsAndCompletions(@Param("learningGoalId") long learningGoalId);

    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            LEFT JOIN FETCH learningGoal.consecutiveCourses courses
            WHERE learningGoal.id = :#{#learningGoalId}
            """)
    Optional<LearningGoal> findByIdWithConsecutiveCourses(@Param("learningGoalId") long learningGoalId);

    @Query("""
            SELECT prerequisite
            FROM LearningGoal prerequisite
            LEFT JOIN FETCH prerequisite.consecutiveCourses courses
            WHERE courses.id = :#{#courseId}
            ORDER BY prerequisite.title""")
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

    @SuppressWarnings("PMD.MethodNamingConventions")
    Page<LearningGoal> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(String partialTitle, String partialCourseTitle, Pageable pageable);

    default LearningGoal findByIdWithLectureUnitsBidirectionalElseThrow(long learningGoalId) {
        return findByIdWithLectureUnitsBidirectional(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default LearningGoal findByIdWithLectureUnitsAndCompletionsElseThrow(long learningGoalId) {
        return findByIdWithLectureUnitsAndCompletions(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

    default LearningGoal findByIdWithConsecutiveCoursesElseThrow(long learningGoalId) {
        return findByIdWithConsecutiveCourses(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

}
