package de.tum.cit.aet.artemis.assessment.repository;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricRating;
import de.tum.cit.aet.artemis.core.exception.ConflictException;

/**
 * Implementation of {@link PresentationAssessmentWriteRepository}.
 *
 * The containing repository is resolved lazily to avoid a circular constructor
 * dependency while retaining access through the repository proxy.
 */
public class PresentationAssessmentWriteRepositoryImpl implements PresentationAssessmentWriteRepository {

    private final ObjectProvider<PresentationAssessmentRepository> assessmentRepository;

    private final PresentationAssessmentInstanceRepository instanceRepository;

    private final PresentationRubricCriterionRepository criterionRepository;

    private final PresentationRubricLevelRepository levelRepository;

    private final PresentationRubricRatingRepository ratingRepository;

    public PresentationAssessmentWriteRepositoryImpl(ObjectProvider<PresentationAssessmentRepository> assessmentRepository,
            PresentationAssessmentInstanceRepository instanceRepository, PresentationRubricCriterionRepository criterionRepository,
            PresentationRubricLevelRepository levelRepository, PresentationRubricRatingRepository ratingRepository) {
        this.assessmentRepository = assessmentRepository;
        this.instanceRepository = instanceRepository;
        this.criterionRepository = criterionRepository;
        this.levelRepository = levelRepository;
        this.ratingRepository = ratingRepository;
    }

    @Override
    public PresentationAssessment createWithRubric(PresentationAssessment assessment, PresentationRubricChanges rubric) {
        PresentationAssessment savedAssessment = assessmentRepository.getObject().save(assessment);
        saveRubric(rubric);
        return savedAssessment;
    }

    @Override
    public void updateWithRubricIfVersionMatches(long courseId, long assessmentId, long expectedVersion, PresentationAssessment assessment, PresentationRubricChanges rubric) {
        requireUnchangedVersion(assessmentRepository.getObject().updateIfVersionMatches(assessmentId, courseId, expectedVersion, assessment.getTitle(), assessment.getDescription(),
                assessment.getMaxPoints(), assessment.getExercise()));
        if (rubric.replacesStoredRubric()) {
            criterionRepository.deleteAllByPresentationAssessmentId(assessmentId);
        }
        saveRubric(rubric);
    }

    @Override
    public PresentationAssessmentInstance updateInstanceIfVersionMatches(long courseId, long assessmentId, long expectedVersion, PresentationAssessmentInstance instance,
            @Nullable List<PresentationRubricRating> ratings) {
        incrementVersionOrThrow(courseId, assessmentId, expectedVersion);
        PresentationAssessmentInstance savedInstance = instanceRepository.save(instance);
        if (ratings != null) {
            ratingRepository.deleteAllByInstanceId(savedInstance.getId());
            ratingRepository.saveAll(ratings);
        }
        return savedInstance;
    }

    @Override
    public List<PresentationAssessmentInstance> createInstancesIfVersionMatches(long courseId, long assessmentId, long expectedVersion,
            List<PresentationAssessmentInstance> instances, List<PresentationRubricRating> ratings) {
        incrementVersionOrThrow(courseId, assessmentId, expectedVersion);
        List<PresentationAssessmentInstance> savedInstances = instanceRepository.saveAll(instances);
        ratingRepository.saveAll(ratings);
        return savedInstances;
    }

    @Override
    public void deleteInstanceIfVersionMatches(long courseId, long assessmentId, long expectedVersion, PresentationAssessmentInstance instance) {
        incrementVersionOrThrow(courseId, assessmentId, expectedVersion);
        instanceRepository.delete(instance);
    }

    /**
     * Advances the parent version or rejects a write based on an outdated state.
     *
     * @param courseId        the owning course id
     * @param assessmentId    the parent presentation assessment id
     * @param expectedVersion the version read before validating the write
     */
    private void incrementVersionOrThrow(long courseId, long assessmentId, long expectedVersion) {
        requireUnchangedVersion(assessmentRepository.getObject().incrementVersionIfMatches(assessmentId, courseId, expectedVersion));
    }

    /**
     * Rejects a write based on an outdated state: a guarded update changes no row once the version of the presentation assessment has moved on.
     *
     * @param updatedRows the number of rows the guarded update changed
     */
    private static void requireUnchangedVersion(int updatedRows) {
        if (updatedRows != 1) {
            throw new ConflictException("The presentation assessment changed while the update was being validated. Please reload and try again.",
                    PresentationAssessment.ENTITY_NAME, "concurrentModification");
        }
    }

    private void saveRubric(PresentationRubricChanges rubric) {
        criterionRepository.saveAll(rubric.criteria());
        levelRepository.saveAll(rubric.levels());
        rubric.instanceIdsByScore().forEach((score, instanceIds) -> instanceRepository.updateRubricScores(instanceIds, score));
    }
}
