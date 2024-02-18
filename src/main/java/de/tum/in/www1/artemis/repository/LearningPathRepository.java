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

    @EntityGraph(type = LOAD, attributePaths = { "user" })
    Optional<LearningPath> findWithEagerUserById(long learningPathId);

    default LearningPath findWithEagerUserByIdElseThrow(long learningPathId) {
        return findWithEagerUserById(learningPathId).orElseThrow(() -> new EntityNotFoundException("LearningPath"));
    }

    @EntityGraph(type = LOAD, attributePaths = { "competencies" })
    Optional<LearningPath> findWithEagerCompetenciesByCourseIdAndUserId(long courseId, long userId);

    default LearningPath findWithEagerCompetenciesByCourseIdAndUserIdElseThrow(long courseId, long userId) {
        return findWithEagerCompetenciesByCourseIdAndUserId(courseId, userId).orElseThrow(() -> new EntityNotFoundException("LearningPath"));
    }

    @EntityGraph(type = LOAD, attributePaths = { "course", "competencies" })
    Optional<LearningPath> findWithEagerCourseAndCompetenciesById(long learningPathId);

    default LearningPath findWithEagerCourseAndCompetenciesByIdElseThrow(long learningPathId) {
        return findWithEagerCourseAndCompetenciesById(learningPathId).orElseThrow(() -> new EntityNotFoundException("LearningPath"));
    }

    @Query("""
            SELECT lp
            FROM LearningPath lp
            WHERE (lp.course.id = :courseId)
                AND (
                    lp.user.login LIKE %:searchTerm%
                    OR CONCAT(lp.user.firstName, ' ', lp.user.lastName) LIKE %:searchTerm%
                )
            """)
    Page<LearningPath> findByLoginOrNameInCourse(@Param("searchTerm") String searchTerm, @Param("courseId") long courseId, Pageable pageable);

    @Query("""
            SELECT COUNT (learningPath)
            FROM LearningPath learningPath
            WHERE learningPath.course.id = :courseId
                AND learningPath.user.isDeleted = FALSE
                AND learningPath.course.studentGroupName MEMBER OF learningPath.user.groups
            """)
    long countLearningPathsOfEnrolledStudentsInCourse(@Param("courseId") long courseId);

    /**
     * Gets a learning path with eagerly fetched competencies, linked lecture units and exercises, and the corresponding domain objects storing the progress.
     * <p>
     * <b>As JPQL does not support conditional JOIN FETCH statements, we have to filter the fetched entities returned in this call.</b>
     * <p>
     * Consider using {@link #findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersByIdElseThrow(long)} instead.
     *
     * @param learningPathId the id of the learning path to fetch
     * @return the learning path with fetched data
     */
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
            """)
    Optional<LearningPath> findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersWithoutJoinConditionById(@Param("learningPathId") long learningPathId);

    /**
     * Gets a learning path with eagerly fetched competencies, linked lecture units and exercises, and the corresponding domain objects storing the progress.
     *
     * @param learningPathId the id of the learning path to fetch
     * @return the learning path with fetched data
     */
    default LearningPath findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersByIdElseThrow(long learningPathId) {
        LearningPath learningPath = findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersWithoutJoinConditionById(learningPathId)
                .orElseThrow(() -> new EntityNotFoundException("LearningPath", learningPathId));
        long userId = learningPath.getUser().getId();
        learningPath.getCompetencies().forEach(competency -> {
            competency.getUserProgress().removeIf(progress -> progress.getUser().getId() != userId);
            competency.getLectureUnits().forEach(lectureUnit -> lectureUnit.getCompletedUsers().removeIf(user -> user.getUser().getId() != userId));
            competency.getExercises().forEach(exercise -> exercise.getStudentParticipations()
                    .removeIf(participation -> participation.getStudent().isEmpty() || participation.getStudent().get().getId() != userId));
        });

        return learningPath;
    }
}
