package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.assessment.domain.PresentationGradingMethod;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricCriterion;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricLevel;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricCriterionDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricLevelDTO;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentInstanceRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricLevelRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricWriteRepository.PresentationRubricChanges;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;

/**
 * Validates, builds and loads the rubric of presentation assessments graded by rubric.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class PresentationRubricService {

    private static final int REQUIRED_WEIGHT_SUM = 100;

    private final PresentationAssessmentRepository presentationAssessmentRepository;

    private final PresentationRubricCriterionRepository presentationRubricCriterionRepository;

    private final PresentationRubricLevelRepository presentationRubricLevelRepository;

    private final PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository;

    public PresentationRubricService(PresentationAssessmentRepository presentationAssessmentRepository, PresentationRubricCriterionRepository presentationRubricCriterionRepository,
            PresentationRubricLevelRepository presentationRubricLevelRepository, PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository) {
        this.presentationAssessmentRepository = presentationAssessmentRepository;
        this.presentationRubricCriterionRepository = presentationRubricCriterionRepository;
        this.presentationRubricLevelRepository = presentationRubricLevelRepository;
        this.presentationAssessmentInstanceRepository = presentationAssessmentInstanceRepository;
    }

    /**
     * Loads the rubric of a presentation graded by rubric together with its instance counts.
     *
     * @param courseId     the owning course id
     * @param assessmentId the presentation assessment id
     * @return the rubric with the number of instances and of assessed instances
     */
    public PresentationRubricDTO getRubric(long courseId, long assessmentId) {
        PresentationAssessment assessment = presentationAssessmentRepository.findOneByIdAndCourseId(assessmentId, courseId)
                .orElseThrow(() -> new EntityNotFoundException(PresentationAssessment.ENTITY_NAME, assessmentId));
        if (assessment.getGradingMethod() != PresentationGradingMethod.RUBRIC) {
            throw new BadRequestAlertException("Only a presentation graded by rubric has a rubric", PresentationAssessment.ENTITY_NAME, "rubricNotAllowed");
        }

        List<PresentationRubricCriterion> criteria = presentationRubricCriterionRepository.findAllByPresentationAssessmentIdOrderByPosition(assessmentId);
        Map<Long, List<PresentationRubricLevel>> levelsByCriterionId = findLevelsByCriterionId(criteria);
        List<PresentationRubricCriterionDTO> criterionDtos = criteria.stream().map(criterion -> toDto(criterion, levelsByCriterionId.getOrDefault(criterion.getId(), List.of())))
                .toList();

        long instanceCount = presentationAssessmentInstanceRepository.countByPresentationAssessmentId(assessmentId);
        long assessedInstanceCount = presentationAssessmentInstanceRepository.countByPresentationAssessmentIdAndRubricScoreIsNotNull(assessmentId);
        return new PresentationRubricDTO(criterionDtos, instanceCount, assessedInstanceCount);
    }

    /**
     * Checks that the rubric criteria of a request match the grading method: required on creation and forbidden for other methods.
     * On an update, omitting the criteria of a presentation graded by rubric leaves its rubric unchanged.
     *
     * @param gradingMethod the grading method of the presentation
     * @param criteria      the rubric criteria of the request, or {@code null} if it has none
     * @param creation      whether the request creates the presentation
     */
    public void validateRubricPresence(PresentationGradingMethod gradingMethod, @Nullable List<PresentationRubricCriterionDTO> criteria, boolean creation) {
        if (gradingMethod == PresentationGradingMethod.RUBRIC) {
            if (creation && criteria == null) {
                throw new BadRequestAlertException("A presentation graded by rubric needs a rubric", PresentationAssessment.ENTITY_NAME, "rubricRequired");
            }
        }
        else if (criteria != null) {
            throw new BadRequestAlertException("Only a presentation graded by rubric can have a rubric", PresentationAssessment.ENTITY_NAME, "rubricNotAllowed");
        }
    }

    /**
     * Builds the rubric entities of a new rubric, replacing whatever rubric the presentation had.
     * Ids in the request are ignored, and the positions follow the order of the request.
     *
     * @param assessment the presentation assessment the rubric belongs to
     * @param criteria   the rubric criteria of the request
     * @return the rubric changes, replacing the stored rubric
     */
    public PresentationRubricChanges buildReplacement(PresentationAssessment assessment, List<PresentationRubricCriterionDTO> criteria) {
        validateContent(criteria);

        List<PresentationRubricCriterion> criterionEntities = new ArrayList<>();
        List<PresentationRubricLevel> levelEntities = new ArrayList<>();
        for (int criterionPosition = 0; criterionPosition < criteria.size(); criterionPosition++) {
            PresentationRubricCriterionDTO criterionDto = criteria.get(criterionPosition);
            PresentationRubricCriterion criterion = new PresentationRubricCriterion();
            criterion.setPresentationAssessment(assessment);
            criterion.setPosition(criterionPosition);
            criterion.setScale(criterionDto.scale());
            applyCriterionTexts(criterion, criterionDto);
            criterionEntities.add(criterion);

            for (int levelPosition = 0; levelPosition < criterionDto.levels().size(); levelPosition++) {
                PresentationRubricLevel level = new PresentationRubricLevel();
                level.setCriterion(criterion);
                level.setPosition(levelPosition);
                level.setDescription(criterionDto.levels().get(levelPosition).description());
                levelEntities.add(level);
            }
        }
        return new PresentationRubricChanges(true, criterionEntities, levelEntities);
    }

    /**
     * Applies texts and weights to the stored rubric of a presentation that already has instances, whose structure must stay the same:
     * the same criteria and levels by id, in the same order, with the same scales.
     *
     * @param assessmentId the presentation assessment id
     * @param criteria     the rubric criteria of the request
     * @return the rubric changes, updating the stored criteria and levels
     */
    public PresentationRubricChanges buildLockedUpdate(long assessmentId, List<PresentationRubricCriterionDTO> criteria) {
        validateContent(criteria);

        List<PresentationRubricCriterion> storedCriteria = presentationRubricCriterionRepository.findAllByPresentationAssessmentIdOrderByPosition(assessmentId);
        Map<Long, List<PresentationRubricLevel>> storedLevelsByCriterionId = findLevelsByCriterionId(storedCriteria);
        if (storedCriteria.size() != criteria.size()) {
            throw structureLocked();
        }

        List<PresentationRubricLevel> levelEntities = new ArrayList<>();
        for (int criterionPosition = 0; criterionPosition < criteria.size(); criterionPosition++) {
            PresentationRubricCriterionDTO criterionDto = criteria.get(criterionPosition);
            PresentationRubricCriterion criterion = storedCriteria.get(criterionPosition);
            if (!criterion.getId().equals(criterionDto.id()) || criterion.getScale() != criterionDto.scale()) {
                throw structureLocked();
            }
            applyCriterionTexts(criterion, criterionDto);

            List<PresentationRubricLevel> storedLevels = storedLevelsByCriterionId.getOrDefault(criterion.getId(), List.of());
            if (storedLevels.size() != criterionDto.levels().size()) {
                throw structureLocked();
            }
            for (int levelPosition = 0; levelPosition < storedLevels.size(); levelPosition++) {
                PresentationRubricLevelDTO levelDto = criterionDto.levels().get(levelPosition);
                PresentationRubricLevel level = storedLevels.get(levelPosition);
                if (!level.getId().equals(levelDto.id())) {
                    throw structureLocked();
                }
                level.setDescription(levelDto.description());
                levelEntities.add(level);
            }
        }
        return new PresentationRubricChanges(false, storedCriteria, levelEntities);
    }

    /**
     * Checks the rules that bean validation cannot express: the weights add up to 100 percent, and every criterion has as many levels as its scale.
     *
     * @param criteria the rubric criteria of the request
     */
    private static void validateContent(List<PresentationRubricCriterionDTO> criteria) {
        int weightSum = criteria.stream().mapToInt(PresentationRubricCriterionDTO::weight).sum();
        if (weightSum != REQUIRED_WEIGHT_SUM) {
            throw new BadRequestAlertException("The weights of the rubric criteria must add up to 100 percent", PresentationAssessment.ENTITY_NAME, "rubricWeightSumInvalid");
        }
        for (PresentationRubricCriterionDTO criterion : criteria) {
            if (criterion.levels().size() != criterion.scale().getLevelCount()) {
                throw new BadRequestAlertException("The number of levels of a rubric criterion must match its scale", PresentationAssessment.ENTITY_NAME, "levelsDoNotMatchScale");
            }
        }
    }

    private static void applyCriterionTexts(PresentationRubricCriterion criterion, PresentationRubricCriterionDTO criterionDto) {
        criterion.setName(criterionDto.name());
        criterion.setDescription(criterionDto.description());
        criterion.setWeight(criterionDto.weight());
    }

    private static BadRequestAlertException structureLocked() {
        return new BadRequestAlertException("Only texts and weights of the rubric can be changed once the presentation has instances", PresentationAssessment.ENTITY_NAME,
                "rubricStructureLocked");
    }

    private Map<Long, List<PresentationRubricLevel>> findLevelsByCriterionId(List<PresentationRubricCriterion> criteria) {
        if (criteria.isEmpty()) {
            return Map.of();
        }
        List<Long> criterionIds = criteria.stream().map(PresentationRubricCriterion::getId).toList();
        return presentationRubricLevelRepository.findAllByCriterionIdInOrderByPosition(criterionIds).stream().collect(Collectors.groupingBy(level -> level.getCriterion().getId()));
    }

    private static PresentationRubricCriterionDTO toDto(PresentationRubricCriterion criterion, List<PresentationRubricLevel> levels) {
        List<PresentationRubricLevelDTO> levelDtos = levels.stream().map(level -> new PresentationRubricLevelDTO(level.getId(), level.getDescription())).toList();
        return new PresentationRubricCriterionDTO(criterion.getId(), criterion.getName(), criterion.getDescription(), criterion.getWeight(), criterion.getScale(), levelDtos);
    }
}
