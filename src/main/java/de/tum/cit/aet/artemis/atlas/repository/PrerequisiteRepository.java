package de.tum.cit.aet.artemis.atlas.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the {@link Prerequisite} entity.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface PrerequisiteRepository extends ArtemisJpaRepository<Prerequisite, Long> {

    @Query("""
            SELECT p
            FROM Prerequisite p
                LEFT JOIN FETCH p.exerciseLinks el
                LEFT JOIN FETCH el.exercise
                LEFT JOIN FETCH p.lectureUnitLinks lul
                LEFT JOIN FETCH lul.lectureUnit lu
                LEFT JOIN FETCH lu.lecture l
                LEFT JOIN FETCH l.attachments
            WHERE p.course.id = :courseId
            """)
    Set<Prerequisite> findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(@Param("courseId") long courseId);

    @Query("""
            SELECT p
            FROM Prerequisite p
                LEFT JOIN FETCH p.lectureUnitLinks lul
                LEFT JOIN FETCH lul.lectureUnit
                LEFT JOIN FETCH p.exerciseLinks el
                LEFT JOIN FETCH el.exercise
            WHERE p.id = :competencyId
            """)
    Optional<Prerequisite> findByIdWithLectureUnitsAndExercises(@Param("competencyId") long competencyId);

    @Query("""
            SELECT p
            FROM Prerequisite p
                LEFT JOIN FETCH p.lectureUnitLinks lul
                LEFT JOIN FETCH lul.lectureUnit
            WHERE p.id = :competencyId
            """)
    Optional<Prerequisite> findByIdWithLectureUnits(@Param("competencyId") long competencyId);

    @Query("""
            SELECT p
            FROM Prerequisite p
            WHERE p.course.id = :courseId
            """)
    Set<Prerequisite> findAllByCourseId(@Param("courseId") long courseId);

    default Prerequisite findByIdWithLectureUnitsAndExercisesElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithLectureUnitsAndExercises(competencyId), competencyId);
    }

    default Prerequisite findByIdWithLectureUnitsElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithLectureUnits(competencyId), competencyId);
    }

    long countByCourseId(long courseId);

    List<Prerequisite> findByCourseIdOrderById(long courseId);

    @Query("""
            SELECT p
            FROM LearningPath lp
                JOIN lp.course.prerequisites p
            WHERE lp.id = :learningPathId
            """)
    Set<Prerequisite> findByLearningPathId(@Param("learningPathId") long learningPathId);

    @Query("""
            SELECT p
            FROM LearningPath lp
                JOIN lp.course.prerequisites p
                LEFT JOIN FETCH p.lectureUnitLinks plul
                LEFT JOIN FETCH plul.lectureUnit
                LEFT JOIN FETCH p.exerciseLinks pel
                LEFT JOIN FETCH pel.exercise
            WHERE lp.id = :learningPathId
            """)
    Set<Prerequisite> findByLearningPathIdWithLectureUnitsAndExercises(@Param("learningPathId") long learningPathId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByCourseId(long courseId);
}
