package de.tum.cit.aet.artemis.presentation.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessment;

/**
 * Spring Data JPA repository for the PresentationAssessment entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PresentationAssessmentRepository extends ArtemisJpaRepository<PresentationAssessment, Long> {

    List<PresentationAssessment> findAllByCourseId(long courseId);

    Optional<PresentationAssessment> findByIdAndCourseId(long id, long courseId);
}
