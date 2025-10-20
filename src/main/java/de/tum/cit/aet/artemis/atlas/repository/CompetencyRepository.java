package de.tum.cit.aet.artemis.atlas.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the Competency entity.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface CompetencyRepository extends ArtemisJpaRepository<Competency, Long>, JpaSpecificationExecutor<Competency> {

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.exerciseLinks el
                LEFT JOIN FETCH el.exercise
                LEFT JOIN FETCH c.lectureUnitLinks lul
                LEFT JOIN FETCH lul.lectureUnit lu
                LEFT JOIN FETCH lu.lecture l
                LEFT JOIN FETCH l.attachments
            WHERE c.course.id = :courseId
            """)
    Set<Competency> findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(@Param("courseId") long courseId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.lectureUnitLinks lul
                LEFT JOIN FETCH lul.lectureUnit lu
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithLectureUnits(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.lectureUnitLinks lul
                LEFT JOIN FETCH lul.lectureUnit
                LEFT JOIN FETCH c.exerciseLinks el
                LEFT JOIN FETCH el.exercise
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithLectureUnitsAndExercises(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
            WHERE c.course.id = :courseId
            """)
    Set<Competency> findAllByCourseId(@Param("courseId") long courseId);

    default Competency findByIdWithLectureUnitsAndExercisesElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithLectureUnitsAndExercises(competencyId), competencyId);
    }

    default Competency findByIdWithLectureUnitsElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithLectureUnits(competencyId), competencyId);
    }

    long countByCourseId(long courseId);

    List<Competency> findByCourseIdOrderById(long courseId);

    @Query("""
            SELECT c
            FROM LearningPath lp
                JOIN lp.course.competencies c
            WHERE lp.id = :learningPathId
            """)
    Set<Competency> findByLearningPathId(@Param("learningPathId") long learningPathId);

    @Query("""
            SELECT c
            FROM LearningPath lp
                JOIN lp.course.competencies c
                LEFT JOIN FETCH c.lectureUnitLinks clul
                LEFT JOIN FETCH clul.lectureUnit
                LEFT JOIN FETCH c.exerciseLinks cel
                LEFT JOIN FETCH cel.exercise
            WHERE lp.id = :learningPathId
            """)
    Set<Competency> findByLearningPathIdWithLectureUnitsAndExercises(@Param("learningPathId") long learningPathId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByCourseId(long courseId);
}
