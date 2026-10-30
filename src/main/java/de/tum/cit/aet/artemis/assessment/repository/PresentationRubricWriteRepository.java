package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricCriterion;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricLevel;

/**
 * Atomic writes of a presentation assessment together with its rubric.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PresentationRubricWriteRepository {

    /**
     * The rubric part of a write: whether it replaces the stored rubric, and the criteria and levels to save.
     *
     * @param replacesStoredRubric whether the stored rubric is deleted before the criteria and levels are saved
     * @param criteria             the criteria to save
     * @param levels               the levels to save
     */
    record PresentationRubricChanges(boolean replacesStoredRubric, List<PresentationRubricCriterion> criteria, List<PresentationRubricLevel> levels) {

        public static final PresentationRubricChanges NONE = new PresentationRubricChanges(false, List.of(), List.of());
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
}
