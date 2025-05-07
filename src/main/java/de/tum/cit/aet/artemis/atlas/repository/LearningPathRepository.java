package de.tum.cit.aet.artemis.atlas.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Conditional(AtlasEnabled.class)
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

    @Query("""
            SELECT LearningPathWithCompetenciesDTO(lp, cc, cp)
            FROM LearningPath lp
            LEFT JOIN FETCH lp.course.competencies cc
            LEFT JOIN FETCH lp.course.prerequisites cp
            WHERE lp.course.id = :courseId
                AND lp.user.id = :userId
            """)
    Optional<LearningPathWithCompetenciesDTO> findDTOWithEagerCompetenciesByCourseIdAndUserId(@Param("courseId") long courseId, @Param("userId") long userId);

    default Optional<LearningPath> findWithEagerCompetenciesByCourseIdAndUserId(long courseId, long userId) {
        return findDTOWithEagerCompetenciesByCourseIdAndUserId(courseId, userId).map(LearningPathWithCompetenciesDTO::toLearningPath);
    }

    @EntityGraph(type = LOAD, attributePaths = { "course" })
    Optional<LearningPath> findWithEagerCourseById(long learningPathId);

    default LearningPath findWithEagerCourseByIdElseThrow(long learningPathId) {
        return getValueElseThrow(findWithEagerCourseById(learningPathId), learningPathId);
    }

    @Query("""
            SELECT LearningPathWithCompetenciesDTO(lp, cc, cp)
            FROM LearningPath lp
            LEFT JOIN FETCH lp.course c
            LEFT JOIN FETCH c.competencies cc
            LEFT JOIN FETCH c.prerequisites cp
            WHERE lp.id = :learningPathId
            """)
    Optional<LearningPathWithCompetenciesDTO> findDTOWithEagerCourseAndCompetenciesById(@Param("learningPathId") long learningPathId);

    default Optional<LearningPath> findWithEagerCourseAndCompetenciesById(long learningPathId) {
        return findDTOWithEagerCourseAndCompetenciesById(learningPathId).map(LearningPathWithCompetenciesDTO::toLearningPath);
    }

    default LearningPath findWithEagerCourseAndCompetenciesByIdElseThrow(long learningPathId) {
        return getValueElseThrow(findWithEagerCourseAndCompetenciesById(learningPathId), learningPathId);
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
                AND learningPath.user.deleted = FALSE
                AND learningPath.course.studentGroupName MEMBER OF learningPath.user.groups
            """)
    long countLearningPathsOfEnrolledStudentsInCourse(@Param("courseId") long courseId);

    @Query("""
            SELECT LearningPathWithCompetenciesDTO(lp, c, p)
            FROM LearningPath l
            LEFT JOIN FETCH l.course.competencies c
            LEFT JOIN FETCH l.course.prerequisites p
            LEFT JOIN FETCH c.lectureUnitLinks clul
            LEFT JOIN FETCH clul.lectureUnit
            LEFT JOIN FETCH p.lectureUnitLinks plul
            LEFT JOIN FETCH plul.lectureUnit
            LEFT JOIN FETCH c.exerciseLinks cel
            LEFT JOIN FETCH cel.exercise
            LEFT JOIN FETCH p.exerciseLinks pel
            LEFT JOIN FETCH pel.exercise
            LEFT JOIN FETCH l.user u
            LEFT JOIN FETCH u.learnerProfile lp
            LEFT JOIN FETCH lp.courseLearnerProfiles clp
            WHERE l.id = :learningPathId
                AND clp.course.id = l.course.id
            """)
    Optional<LearningPathWithCompetenciesDTO> findDTOWithCompetenciesAndLectureUnitsAndExercisesAndLearnerProfileById(@Param("learningPathId") long learningPathId);

    default Optional<LearningPath> findWithCompetenciesAndLectureUnitsAndExercisesAndLearnerProfileById(@Param("learningPathId") long learningPathId) {
        return findDTOWithCompetenciesAndLectureUnitsAndExercisesAndLearnerProfileById(learningPathId).map(LearningPathWithCompetenciesDTO::toLearningPath);
    }

    default LearningPath findWithCompetenciesAndLectureUnitsAndExercisesAndLearnerProfileByIdElseThrow(long learningPathId) {
        return getValueElseThrow(findWithCompetenciesAndLectureUnitsAndExercisesAndLearnerProfileById(learningPathId), learningPathId);
    }

    record LearningPathWithCompetenciesDTO(LearningPath learningPath, Set<Competency> competencies, Set<Prerequisite> prerequisites) {

        public LearningPath toLearningPath() {
            final var learningPath = this.learningPath;
            final var competencies = new HashSet<CourseCompetency>(prerequisites());
            competencies.addAll(competencies());
            learningPath.setCompetencies(competencies);
            return learningPath;
        }
    }
}
