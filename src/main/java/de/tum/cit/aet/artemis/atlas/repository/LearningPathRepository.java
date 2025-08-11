package de.tum.cit.aet.artemis.atlas.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathRepositoryService;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the {@link LearningPath} entity.
 * Important: For fetching a learning path with its competencies, use the {@link LearningPathRepositoryService} instead of this repository.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface LearningPathRepository extends ArtemisJpaRepository<LearningPath, Long> {

    Optional<LearningPath> findByCourseIdAndUserId(long courseId, long userId);

    default LearningPath findByCourseIdAndUserIdElseThrow(long courseId, long userId) {
        return getValueElseThrow(findByCourseIdAndUserId(courseId, userId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "user" })
    Optional<LearningPath> findWithEagerUserById(long learningPathId);

    default LearningPath findWithEagerUserByIdElseThrow(long learningPathId) {
        return getValueElseThrow(findWithEagerUserById(learningPathId), learningPathId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "course" })
    Optional<LearningPath> findWithEagerCourseById(long learningPathId);

    default LearningPath findWithEagerCourseByIdElseThrow(long learningPathId) {
        return getValueElseThrow(findWithEagerCourseById(learningPathId), learningPathId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "user", "course" })
    Optional<LearningPath> findWithEagerUserAndCourseById(long learningPathId);

    default LearningPath findWithEagerUserAndCourseByIdElseThrow(long learningPathId) {
        return getValueElseThrow(findWithEagerUserAndCourseById(learningPathId), learningPathId);
    }

    @Query("""
            SELECT lp
            FROM LearningPath lp
            JOIN FETCH lp.user
            WHERE (lp.course.id = :courseId)
                AND (
                    lp.user.login LIKE %:searchTerm%
                    OR CONCAT(lp.user.firstName, ' ', lp.user.lastName) LIKE %:searchTerm%
                )
            """)
    Page<LearningPath> findWithEagerUserByLoginOrNameInCourse(@Param("searchTerm") String searchTerm, @Param("courseId") long courseId, Pageable pageable);

    @Query("""
            SELECT COUNT (learningPath)
            FROM LearningPath learningPath
            WHERE learningPath.course.id = :courseId
                AND learningPath.user.deleted = FALSE
                AND learningPath.course.studentGroupName MEMBER OF learningPath.user.groups
            """)
    long countLearningPathsOfEnrolledStudentsInCourse(@Param("courseId") long courseId);

    @Query("""
            SELECT l
            FROM LearningPath l
            LEFT JOIN FETCH l.user u
            LEFT JOIN FETCH u.learnerProfile lp
            LEFT JOIN FETCH lp.courseLearnerProfiles clp
            WHERE l.id = :learningPathId
                AND clp.course.id = l.course.id
            """)
    Optional<LearningPath> findWithEagerUserAndLearnerProfileById(@Param("learningPathId") long learningPathId);

    @Query("""
            SELECT lp
            FROM LearningPath lp
            WHERE lp.course.id = :courseId
                AND lp.user.deleted = FALSE
                AND lp.course.studentGroupName MEMBER OF lp.user.groups
            """)
    List<LearningPath> findAllByCourseIdForEnrolledStudents(@Param("courseId") long courseId);
}
