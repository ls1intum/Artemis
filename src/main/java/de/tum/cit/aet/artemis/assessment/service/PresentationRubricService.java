package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.assessment.domain.PresentationGradingMethod;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricCriterion;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricLevel;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricRating;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricCriterionDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricLevelDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricRatingDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricRatingLevelDTO;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentInstanceRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricLevelRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricRatingRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricWriteRepository.PresentationRubricChanges;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.util.RoundingUtil;

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

    private final PresentationRubricRatingRepository presentationRubricRatingRepository;

    public PresentationRubricService(PresentationAssessmentRepository presentationAssessmentRepository, PresentationRubricCriterionRepository presentationRubricCriterionRepository,
            PresentationRubricLevelRepository presentationRubricLevelRepository, PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository,
            PresentationRubricRatingRepository presentationRubricRatingRepository) {
        this.presentationAssessmentRepository = presentationAssessmentRepository;
        this.presentationRubricCriterionRepository = presentationRubricCriterionRepository;
        this.presentationRubricLevelRepository = presentationRubricLevelRepository;
        this.presentationAssessmentInstanceRepository = presentationAssessmentInstanceRepository;
        this.presentationRubricRatingRepository = presentationRubricRatingRepository;
    }

    /**
     * A validated rubric result: the selected level of every criterion with its comment, and the score they add up to.
     * It creates the rating entities for each instance it is applied to, so a batch shares one validation.
     *
     * @param ratings the validated ratings, one per criterion
     * @param score   the rubric score in percent, rounded by the accuracy of the course
     */
    public record RubricGrade(List<ResolvedRating> ratings, double score) {

        /**
         * Creates the rating entities of this grade for an instance.
         *
         * @param instance the instance the ratings belong to
         * @return new rating entities
         */
        public List<PresentationRubricRating> ratingsFor(PresentationAssessmentInstance instance) {
            return ratings.stream().map(resolved -> {
                PresentationRubricRating rating = new PresentationRubricRating();
                rating.setInstance(instance);
                rating.setCriterion(resolved.criterion());
                rating.setLevel(resolved.level());
                rating.setComment(resolved.comment());
                return rating;
            }).toList();
        }
    }

    /**
     * A rating whose criterion and level are the stored entities of the rubric.
     *
     * @param criterion the rated criterion
     * @param level     the selected level of the criterion
     * @param comment   the optional comment of the rating
     */
    public record ResolvedRating(PresentationRubricCriterion criterion, PresentationRubricLevel level, @Nullable String comment) {
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
        return new PresentationRubricChanges(true, criterionEntities, levelEntities, Map.of());
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
        boolean weightsChanged = false;
        for (int criterionPosition = 0; criterionPosition < criteria.size(); criterionPosition++) {
            PresentationRubricCriterionDTO criterionDto = criteria.get(criterionPosition);
            PresentationRubricCriterion criterion = storedCriteria.get(criterionPosition);
            if (!criterion.getId().equals(criterionDto.id()) || criterion.getScale() != criterionDto.scale()) {
                throw structureLocked();
            }
            weightsChanged |= criterion.getWeight() != criterionDto.weight();
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
        Map<Long, Double> recalculatedScores = weightsChanged ? recalculateScores(assessmentId, storedCriteria) : Map.of();
        return new PresentationRubricChanges(false, storedCriteria, levelEntities, recalculatedScores);
    }

    /**
     * Validates the ratings of a request against the rubric and calculates the score: every criterion needs exactly one rating, and its level must belong to it.
     *
     * @param assessmentId the presentation assessment id
     * @param ratings      the ratings of the request
     * @return the validated grade with its rounded score
     */
    public RubricGrade grade(long assessmentId, List<PresentationRubricRatingDTO> ratings) {
        List<PresentationRubricCriterion> criteria = presentationRubricCriterionRepository.findAllByPresentationAssessmentIdOrderByPosition(assessmentId);
        Map<Long, List<PresentationRubricLevel>> levelsByCriterionId = findLevelsByCriterionId(criteria);
        Map<Long, PresentationRubricRatingDTO> ratingByCriterionId = ratings.stream()
                .collect(Collectors.toMap(PresentationRubricRatingDTO::criterionId, Function.identity(), (first, second) -> {
                    throw ratingsIncomplete();
                }));
        if (ratingByCriterionId.size() != criteria.size() || !criteria.stream().map(PresentationRubricCriterion::getId).allMatch(ratingByCriterionId::containsKey)) {
            throw ratingsIncomplete();
        }

        List<ResolvedRating> resolved = new ArrayList<>();
        Map<Long, Integer> positionByCriterionId = new HashMap<>();
        for (PresentationRubricCriterion criterion : criteria) {
            PresentationRubricRatingDTO rating = ratingByCriterionId.get(criterion.getId());
            PresentationRubricLevel level = levelsByCriterionId.getOrDefault(criterion.getId(), List.of()).stream().filter(candidate -> candidate.getId().equals(rating.levelId()))
                    .findFirst().orElseThrow(() -> new BadRequestAlertException("The selected level does not belong to the rated criterion",
                            PresentationAssessmentInstance.ENTITY_NAME, "rubricLevelInvalid"));
            resolved.add(new ResolvedRating(criterion, level, rating.comment()));
            positionByCriterionId.put(criterion.getId(), level.getPosition());
        }
        int accuracy = presentationAssessmentRepository.findAccuracyOfScoresById(assessmentId);
        return new RubricGrade(resolved, calculateScore(criteria, positionByCriterionId, accuracy));
    }

    /**
     * Recalculates the scores of all assessed instances with the new weights of the criteria.
     *
     * @param assessmentId the presentation assessment id
     * @param criteria     the criteria with their new weights
     * @return the new score of every assessed instance by instance id
     */
    private Map<Long, Double> recalculateScores(long assessmentId, List<PresentationRubricCriterion> criteria) {
        int accuracy = presentationAssessmentRepository.findAccuracyOfScoresById(assessmentId);
        Map<Long, Map<Long, Integer>> positionsByInstanceId = presentationRubricRatingRepository.findLevelPositionsByPresentationAssessmentId(assessmentId).stream()
                .collect(Collectors.groupingBy(PresentationRubricRatingLevelDTO::instanceId,
                        Collectors.toMap(PresentationRubricRatingLevelDTO::criterionId, PresentationRubricRatingLevelDTO::levelPosition)));
        Map<Long, Double> scores = new HashMap<>();
        positionsByInstanceId.forEach((instanceId, positions) -> scores.put(instanceId, calculateScore(criteria, positions, accuracy)));
        return scores;
    }

    /**
     * Calculates the rubric score: each criterion contributes its weight times the share of its scale the selected level reaches.
     * Only the sum is rounded, so rounding errors of the single criteria do not add up.
     *
     * @param criteria              the criteria with their weights and scales
     * @param positionByCriterionId the selected level position of every criterion, starting at 0 for the lowest level
     * @param accuracy              the number of decimal places of the course
     * @return the score in percent
     */
    private static double calculateScore(List<PresentationRubricCriterion> criteria, Map<Long, Integer> positionByCriterionId, int accuracy) {
        double score = 0;
        for (PresentationRubricCriterion criterion : criteria) {
            double share = (double) positionByCriterionId.get(criterion.getId()) / (criterion.getScale().getLevelCount() - 1);
            score += criterion.getWeight() * share;
        }
        return RoundingUtil.roundToNDecimalPlaces(score, accuracy);
    }

    private static BadRequestAlertException ratingsIncomplete() {
        return new BadRequestAlertException("Every criterion of the rubric needs exactly one rating", PresentationAssessmentInstance.ENTITY_NAME, "rubricRatingsIncomplete");
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
