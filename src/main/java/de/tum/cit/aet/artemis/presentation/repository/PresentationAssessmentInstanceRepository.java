package de.tum.cit.aet.artemis.presentation.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessmentInstance;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PresentationAssessmentInstanceRepository extends ArtemisJpaRepository<PresentationAssessmentInstance, Long> {

    @EntityGraph(attributePaths = { "students", "presentationAssessment", "presentationAssessment.course" })
    Optional<PresentationAssessmentInstance> findByIdAndPresentationAssessmentIdAndPresentationAssessmentCourseId(long id, long assessmentId, long courseId);
}
