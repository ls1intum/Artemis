package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricRating;

/**
 * Atomic instance writes guarded by the parent presentation assessment version.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PresentationAssessmentInstanceWriteRepository {

    /**
     * Updates an instance with its rubric ratings and increments its parent assessment version atomically.
     *
     * @param courseId        the owning course id
     * @param assessmentId    the parent presentation assessment id
     * @param expectedVersion the parent version read before validating the update
     * @param instance        the instance containing the validated changes
     * @param ratings         the ratings replacing the stored ones, or {@code null} to keep them
     * @return the saved instance
     */
    @Transactional
    PresentationAssessmentInstance updateInstanceIfVersionMatches(long courseId, long assessmentId, long expectedVersion, PresentationAssessmentInstance instance,
            @Nullable List<PresentationRubricRating> ratings);

    /**
     * Creates instances with their rubric ratings and increments their parent assessment version atomically.
     *
     * @param courseId        the owning course id
     * @param assessmentId    the parent presentation assessment id
     * @param expectedVersion the parent version read before validating the creation
     * @param instances       the instances containing the validated data
     * @param ratings         the ratings of the new instances, empty if they are not assessed by rubric
     * @return the saved instances
     */
    @Transactional
    List<PresentationAssessmentInstance> createInstancesIfVersionMatches(long courseId, long assessmentId, long expectedVersion, List<PresentationAssessmentInstance> instances,
            List<PresentationRubricRating> ratings);

    /**
     * Deletes an instance and increments its parent assessment version atomically, so that a concurrent update of the deleted instance is rejected.
     *
     * @param courseId        the owning course id
     * @param assessmentId    the parent presentation assessment id
     * @param expectedVersion the parent version read before the instance was loaded
     * @param instance        the instance to delete
     */
    @Transactional
    void deleteInstanceIfVersionMatches(long courseId, long assessmentId, long expectedVersion, PresentationAssessmentInstance instance);
}
