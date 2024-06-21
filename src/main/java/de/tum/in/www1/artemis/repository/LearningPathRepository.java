package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Profile(PROFILE_CORE)
@Repository
public interface LearningPathRepository extends ArtemisJpaRepository<LearningPath, Long> {

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

    @EntityGraph(type = LOAD, attributePaths = { "competencies", "competencies.lectureUnits", "competencies.exercises" })
    Optional<LearningPath> findWithCompetenciesAndLectureUnitsAndExercisesById(long learningPathId);

    default LearningPath findWithCompetenciesAndLectureUnitsAndExercisesByIdElseThrow(long learningPathId) {
        return findWithCompetenciesAndLectureUnitsAndExercisesById(learningPathId).orElseThrow(() -> new EntityNotFoundException("LearningPath"));
    }
}
