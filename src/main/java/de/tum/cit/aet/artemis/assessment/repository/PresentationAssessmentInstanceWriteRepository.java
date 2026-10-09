package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance;

/**
 * Atomic instance writes guarded by the parent presentation assessment version.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PresentationAssessmentInstanceWriteRepository {

    /**
     * Updates an instance and increments its parent assessment version atomically.
     *
     * @param courseId        the owning course id
     * @param assessmentId    the parent presentation assessment id
     * @param expectedVersion the parent version read before validating the update
     * @param instance        the instance containing the validated changes
     * @return the saved instance
     */
    @Transactional
    PresentationAssessmentInstance updateInstanceIfVersionMatches(long courseId, long assessmentId, long expectedVersion, PresentationAssessmentInstance instance);

    /**
     * Creates instances and increments their parent assessment version atomically.
     *
     * @param courseId        the owning course id
     * @param assessmentId    the parent presentation assessment id
     * @param expectedVersion the parent version read before validating the creation
     * @param instances       the instances containing the validated data
     * @return the saved instances
     */
    @Transactional
    List<PresentationAssessmentInstance> createInstancesIfVersionMatches(long courseId, long assessmentId, long expectedVersion, List<PresentationAssessmentInstance> instances);
}
