package de.tum.cit.aet.artemis.atlas.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the Competency entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CompetencyRepository extends ArtemisJpaRepository<Competency, Long>, JpaSpecificationExecutor<Competency> {

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.exercises
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH lu.lecture l
                LEFT JOIN FETCH l.attachments
            WHERE c.course.id = :courseId
            """)
    Set<Competency> findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(@Param("courseId") long courseId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.lectureUnits lu
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithLectureUnits(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH c.exercises
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithLectureUnitsAndExercises(@Param("competencyId") long competencyId);

    default Competency findByIdWithLectureUnitsAndExercisesElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithLectureUnitsAndExercises(competencyId), competencyId);
    }

    default Competency findByIdWithLectureUnitsElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithLectureUnits(competencyId), competencyId);
    }

    long countByCourse(Course course);

    List<Competency> findByCourseIdOrderById(long courseId);
}
