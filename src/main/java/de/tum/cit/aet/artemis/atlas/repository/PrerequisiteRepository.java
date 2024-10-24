package de.tum.cit.aet.artemis.atlas.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the {@link Prerequisite} entity.
 */
public interface PrerequisiteRepository extends ArtemisJpaRepository<Prerequisite, Long> {

    List<Prerequisite> findAllByCourseIdOrderById(long courseId);

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

    default Prerequisite findByIdWithLectureUnitsAndExercisesElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithLectureUnitsAndExercises(competencyId), competencyId);
    }

    default Prerequisite findByIdWithLectureUnitsElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithLectureUnits(competencyId), competencyId);
    }

    long countByCourse(Course course);

    List<Prerequisite> findByCourseIdOrderById(long courseId);
}
