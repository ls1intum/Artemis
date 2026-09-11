package de.tum.cit.aet.artemis.presentation.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessment;

/**
 * Spring Data JPA repository for the PresentationAssessment entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PresentationAssessmentRepository extends ArtemisJpaRepository<PresentationAssessment, Long> {

    @EntityGraph(attributePaths = { "exercise", "instances", "instances.students" })
    List<PresentationAssessment> findAllByCourseId(long courseId);

    @EntityGraph(attributePaths = { "exercise", "instances", "instances.students" })
    Optional<PresentationAssessment> findByIdAndCourseId(long id, long courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT assessment FROM PresentationAssessment assessment WHERE assessment.id = :id AND assessment.course.id = :courseId")
    Optional<PresentationAssessment> findByIdAndCourseIdForUpdate(@Param("id") long id, @Param("courseId") long courseId);

    default PresentationAssessment findByIdAndCourseIdElseThrow(long id, long courseId) {
        return findByIdAndCourseId(id, courseId).orElseThrow(() -> new EntityNotFoundException(PresentationAssessment.ENTITY_NAME, id));
    }

    /**
     * Runs a presentation-assessment mutation while holding a pessimistic lock on the parent assessment. All assessment and instance writes use this transaction so that
     * maximum-point validation and result-point persistence cannot race.
     *
     * @param id        the presentation assessment id
     * @param courseId  the owning course id
     * @param operation the mutation to run with the lock held
     * @param <T>       the mutation result type
     * @return the mutation result
     */
    @Transactional
    default <T> T executeWithWriteLock(long id, long courseId, Function<PresentationAssessment, T> operation) {
        PresentationAssessment assessment = findByIdAndCourseIdForUpdate(id, courseId).orElseThrow(() -> new EntityNotFoundException(PresentationAssessment.ENTITY_NAME, id));
        return operation.apply(assessment);
    }

}
