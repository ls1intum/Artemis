package de.tum.cit.aet.artemis.assessment.repository;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.core.exception.ConflictException;

/**
 * Implementation of {@link PresentationAssessmentInstanceWriteRepository}.
 *
 * The containing repository is resolved lazily to avoid a circular constructor
 * dependency while retaining access through the repository proxy.
 */
public class PresentationAssessmentInstanceWriteRepositoryImpl implements PresentationAssessmentInstanceWriteRepository {

    private final PresentationAssessmentRepository assessmentRepository;

    private final ObjectProvider<PresentationAssessmentInstanceRepository> instanceRepository;

    public PresentationAssessmentInstanceWriteRepositoryImpl(PresentationAssessmentRepository assessmentRepository,
            ObjectProvider<PresentationAssessmentInstanceRepository> instanceRepository) {
        this.assessmentRepository = assessmentRepository;
        this.instanceRepository = instanceRepository;
    }

    @Override
    public PresentationAssessmentInstance updateInstanceIfVersionMatches(long courseId, long assessmentId, long expectedVersion, PresentationAssessmentInstance instance) {
        incrementVersionOrThrow(courseId, assessmentId, expectedVersion);
        return instanceRepository.getObject().save(instance);
    }

    @Override
    public List<PresentationAssessmentInstance> createInstancesIfVersionMatches(long courseId, long assessmentId, long expectedVersion,
            List<PresentationAssessmentInstance> instances) {
        incrementVersionOrThrow(courseId, assessmentId, expectedVersion);
        return instanceRepository.getObject().saveAll(instances);
    }

    /**
     * Advances the parent version or rejects a write based on an outdated state.
     *
     * @param courseId        the owning course id
     * @param assessmentId    the parent presentation assessment id
     * @param expectedVersion the version read before validating the write
     */
    private void incrementVersionOrThrow(long courseId, long assessmentId, long expectedVersion) {
        int updated = assessmentRepository.incrementVersionIfMatches(assessmentId, courseId, expectedVersion);

        if (updated != 1) {
            throw new ConflictException("The presentation assessment changed while the update was being validated. Please reload and try again.",
                    PresentationAssessment.ENTITY_NAME, "concurrentModification");
        }
    }
}
