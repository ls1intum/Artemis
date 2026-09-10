package de.tum.cit.aet.artemis.presentation.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

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

    default PresentationAssessment findByIdAndCourseIdElseThrow(long id, long courseId) {
        return findByIdAndCourseId(id, courseId).orElseThrow(() -> new EntityNotFoundException(PresentationAssessment.ENTITY_NAME, id));
    }

}
