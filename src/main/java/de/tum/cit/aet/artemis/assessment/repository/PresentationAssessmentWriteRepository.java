package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricCriterion;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricLevel;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricRating;

/**
 * Atomic writes of a presentation assessment together with its rubric or its instances; every write to an existing presentation assessment is guarded by its version.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PresentationAssessmentWriteRepository {

    /**
     * The rubric part of a write: whether it replaces the stored rubric, the criteria and levels to save, and the recalculated scores of assessed instances.
     *
     * @param replacesStoredRubric whether the stored rubric is deleted before the criteria and levels are saved
     * @param criteria             the criteria to save
     * @param levels               the levels to save
     * @param instanceIdsByScore   the assessed instances grouped by their recalculated rubric score, empty if no weight changed
     */
    record PresentationRubricChanges(boolean replacesStoredRubric, List<PresentationRubricCriterion> criteria, List<PresentationRubricLevel> levels,
            Map<Double, List<Long>> instanceIdsByScore) {

        public static final PresentationRubricChanges NONE = new PresentationRubricChanges(false, List.of(), List.of(), Map.of());
    }

    /**
     * Creates a presentation assessment together with its rubric atomically.
     *
     * @param assessment the presentation assessment containing the validated data
     * @param rubric     the rubric to save with it
     * @return the saved presentation assessment
     */
    @Transactional
    PresentationAssessment createWithRubric(PresentationAssessment assessment, PresentationRubricChanges rubric);

    /**
     * Updates a presentation assessment together with its rubric and increments its version atomically.
     *
     * @param courseId        the owning course id
     * @param assessmentId    the presentation assessment id
     * @param expectedVersion the version read before validating the update
     * @param assessment      the presentation assessment containing the validated changes
     * @param rubric          the rubric changes to save with it
     */
    @Transactional
    void updateWithRubricIfVersionMatches(long courseId, long assessmentId, long expectedVersion, PresentationAssessment assessment, PresentationRubricChanges rubric);

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
