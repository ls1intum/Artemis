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
            WHERE (lp.course.id = :courseId) AND (lp.user.login LIKE %:searchTerm% OR CONCAT(lp.user.firstName, ' ', lp.user.lastName) LIKE %:searchTerm%)
            """)
    Page<LearningPath> findByLoginOrNameInCourse(@Param("searchTerm") String searchTerm, @Param("courseId") long courseId, Pageable pageable);

    @Query("""
            SELECT COUNT (learningPath)
            FROM LearningPath learningPath
            WHERE learningPath.course.id = :courseId AND learningPath.user.isDeleted = false AND learningPath.course.studentGroupName MEMBER OF learningPath.user.groups
            """)
    long countLearningPathsOfEnrolledStudentsInCourse(@Param("courseId") long courseId);

    /**
     * Gets a learning path with eagerly fetched competencies, linked lecture units and exercises, and the corresponding domain objects storing the progress.
     * <p>
     * The query only fetches data related to the owner of the learning path. participations and progress for other users are not included.
     * IMPORTANT: JPA doesn't support JOIN-FETCH-ON statements. To fetch the relevant data we utilize the entity graph annotation.
     * Moving the ON clauses to the WHERE clause would result in significantly different and faulty output.
     *
     * @param learningPathId the id of the learning path to fetch
     * @return the learning path with fetched data
     */
    @Query("""
            SELECT learningPath
            FROM LearningPath learningPath
                LEFT JOIN FETCH learningPath.competencies competencies
                LEFT JOIN competencies.userProgress progress
                    ON competencies.id = progress.learningGoal.id AND progress.user.id = learningPath.user.id
                LEFT JOIN FETCH competencies.lectureUnits lectureUnits
                LEFT JOIN lectureUnits.completedUsers completedUsers
                    ON lectureUnits.id = completedUsers.lectureUnit.id AND completedUsers.user.id = learningPath.user.id
                LEFT JOIN FETCH competencies.exercises exercises
                LEFT JOIN exercises.studentParticipations studentParticipations
                    ON exercises.id = studentParticipations.exercise.id AND studentParticipations.student.id = learningPath.user.id
            WHERE learningPath.id = :learningPathId
            """)
    @EntityGraph(type = LOAD, attributePaths = { "competencies.userProgress", "competencies.lectureUnits.completedUsers", "competencies.exercises.studentParticipations" })
    Optional<LearningPath> findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersById(@Param("learningPathId") long learningPathId);

    default LearningPath findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersByIdElseThrow(long learningPathId) {
        return findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersById(learningPathId)
                .orElseThrow(() -> new EntityNotFoundException("LearningPath", learningPathId));
    }
}
