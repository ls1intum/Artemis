package de.tum.cit.aet.artemis.assessment.repository;

import org.springframework.beans.factory.ObjectProvider;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.core.exception.ConflictException;

/**
 * Implementation of {@link PresentationRubricWriteRepository}.
 *
 * The containing repository is resolved lazily to avoid a circular constructor
 * dependency while retaining access through the repository proxy.
 */
public class PresentationRubricWriteRepositoryImpl implements PresentationRubricWriteRepository {

    private final PresentationAssessmentRepository assessmentRepository;

    private final ObjectProvider<PresentationRubricCriterionRepository> criterionRepository;

    private final PresentationRubricLevelRepository levelRepository;

    private final PresentationAssessmentInstanceRepository instanceRepository;

    public PresentationRubricWriteRepositoryImpl(PresentationAssessmentRepository assessmentRepository, ObjectProvider<PresentationRubricCriterionRepository> criterionRepository,
            PresentationRubricLevelRepository levelRepository, PresentationAssessmentInstanceRepository instanceRepository) {
        this.assessmentRepository = assessmentRepository;
        this.criterionRepository = criterionRepository;
        this.levelRepository = levelRepository;
        this.instanceRepository = instanceRepository;
    }

    @Override
    public PresentationAssessment createWithRubric(PresentationAssessment assessment, PresentationRubricChanges rubric) {
        PresentationAssessment savedAssessment = assessmentRepository.save(assessment);
        saveRubric(rubric);
        return savedAssessment;
    }

    @Override
    public void updateWithRubricIfVersionMatches(long courseId, long assessmentId, long expectedVersion, PresentationAssessment assessment, PresentationRubricChanges rubric) {
        int updated = assessmentRepository.updateIfVersionMatches(assessmentId, courseId, expectedVersion, assessment.getTitle(), assessment.getDescription(),
                assessment.getMaxPoints(), assessment.getExercise());

        if (updated != 1) {
            throw new ConflictException("The presentation assessment changed while the update was being validated. Please reload and try again.",
                    PresentationAssessment.ENTITY_NAME, "concurrentModification");
        }
        if (rubric.replacesStoredRubric()) {
            criterionRepository.getObject().deleteAllByPresentationAssessmentId(assessmentId);
        }
        saveRubric(rubric);
    }

    private void saveRubric(PresentationRubricChanges rubric) {
        criterionRepository.getObject().saveAll(rubric.criteria());
        levelRepository.saveAll(rubric.levels());
        rubric.instanceIdsByScore().forEach((score, instanceIds) -> instanceRepository.updateRubricScores(instanceIds, score));
    }
}
