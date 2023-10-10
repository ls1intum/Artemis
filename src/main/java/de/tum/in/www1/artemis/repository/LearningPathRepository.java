package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface LearningPathRepository extends JpaRepository<LearningPath, Long> {

    Optional<LearningPath> findByCourseIdAndUserId(long courseId, long userId);

    default LearningPath findByCourseIdAndUserIdElseThrow(long courseId, long userId) {
        return findByCourseIdAndUserId(courseId, userId).orElseThrow(() -> new EntityNotFoundException("LearningPath"));
    }

    @EntityGraph(type = LOAD, attributePaths = { "competencies" })
    Optional<LearningPath> findWithEagerCompetenciesByCourseIdAndUserId(long courseId, long userId);

    default LearningPath findWithEagerCompetenciesByCourseIdAndUserIdElseThrow(long courseId, long userId) {
        return findWithEagerCompetenciesByCourseIdAndUserId(courseId, userId).orElseThrow(() -> new EntityNotFoundException("LearningPath"));
    }

    @Query("""
            SELECT lp
            FROM LearningPath lp
            WHERE (lp.course.id = :courseId) AND (lp.user.login LIKE %:searchTerm% OR CONCAT(lp.user.firstName, ' ', lp.user.lastName) LIKE %:searchTerm%)
            """)
    Page<LearningPath> findByLoginOrNameInCourse(@Param("searchTerm") String searchTerm, @Param("courseId") long courseId, Pageable pageable);

    @Query("""
            SELECT COUNT (learningPath)
            FROM LearningPath learningPath
            WHERE learningPath.course.id = :courseId AND learningPath.user.isDeleted = false AND learningPath.course.studentGroupName MEMBER OF learningPath.user.groups
            """)
    long countLearningPathsOfEnrolledStudentsInCourse(@Param("courseId") long courseId);

    @Query("""
            SELECT learningPath
            FROM LearningPath learningPath
                LEFT JOIN FETCH learningPath.competencies competencies
                LEFT JOIN FETCH competencies.userProgress progress
                LEFT JOIN FETCH competencies.lectureUnits lectureUnits
                LEFT JOIN FETCH lectureUnits.completedUsers completedUsers
                LEFT JOIN FETCH competencies.exercises exercises
                LEFT JOIN FETCH exercises.studentParticipations studentParticipations
            WHERE learningPath.id = :learningPathId
                AND (progress IS NULL OR progress.user.id = learningPath.user.id)
                AND (completedUsers IS NULL OR completedUsers.user.id = learningPath.user.id)
                AND (studentParticipations IS NULL OR studentParticipations.student.id = learningPath.user.id)
            """)
    Optional<LearningPath> findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersById(@Param("learningPathId") long learningPathId);

    default LearningPath findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersByIdElseThrow(long learningPathId) {
        return findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersById(learningPathId)
                .orElseThrow(() -> new EntityNotFoundException("LearningPath", learningPathId));
    }
}
