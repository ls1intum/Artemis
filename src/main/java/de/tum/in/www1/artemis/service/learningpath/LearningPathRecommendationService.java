package de.tum.in.www1.artemis.service.learningpath;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LearningObject;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.domain.competency.RelationType;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.CompetencyRelationRepository;
import de.tum.in.www1.artemis.service.LearningObjectService;
import de.tum.in.www1.artemis.service.ParticipantScoreService;
import de.tum.in.www1.artemis.service.competency.CompetencyProgressService;

/**
 * Service Implementation for the recommendation of competencies and learning objects in learning paths.
 */
@Profile(PROFILE_CORE)
@Service
public class LearningPathRecommendationService {

    private final CompetencyRelationRepository competencyRelationRepository;

    private final LearningObjectService learningObjectService;

    private final ParticipantScoreService participantScoreService;

    /**
     * Base utility that is used to calculate a competencies' utility with respect to the earliest due date of the competency.
     */
    private static final double DUE_DATE_UTILITY = 10;

    /**
     * Base utility that is used to calculate a competencies' utility with respect to the number of mastered prior competencies.
     */
    private static final double PRIOR_UTILITY = 150;

    /**
     * Base utility and ratios that are used to calculate a competencies' utility with respect to the number of competencies that this competency extends or assumes.
     * <p>
     * Ratios donate the importance of the relation compared to the other.
     * Important: EXTENDS_UTILITY_RATIO should be smaller than ASSUMES_UTILITY_RATIO to prefer extends-relation to assumes-relations.
     */
    private static final double EXTENDS_UTILITY_RATIO = 1;

    private static final double ASSUMES_UTILITY_RATIO = 2;

    private static final double EXTENDS_OR_ASSUMES_UTILITY = 100;

    /**
     * Base utility that is used to calculate a competencies' utility with respect to the mastery level.
     */
    private static final double MASTERY_PROGRESS_UTILITY = 1;

    private static final double SCORE_THRESHOLD = 80;

    /**
     * Lookup table containing the distribution of exercises by difficulty level that should be recommended.
     * <p>
     * Values can be reproduced by computing the cumulative normal distribution function (cdf) for the general normal distribution f(x|mean=[0.00...1.00], std_dev=0.35): {easy:
     * cdf(0.40), medium: cdf(0.85) - cdf(0.40), hard: 1 - cdf(0.85)}.
     * Each array corresponds to the mean=idx/#distributions.
     */
    private static final double[][] EXERCISE_DIFFICULTY_DISTRIBUTION_LUT = new double[][] { { 0.87, 0.12, 0.01 }, { 0.80, 0.18, 0.02 }, { 0.72, 0.25, 0.03 }, { 0.61, 0.33, 0.06 },
            { 0.50, 0.40, 0.10 }, { 0.39, 0.45, 0.16 }, { 0.28, 0.48, 0.24 }, { 0.20, 0.47, 0.33 }, { 0.13, 0.43, 0.44 }, { 0.08, 0.37, 0.55 }, { 0.04, 0.29, 0.67 }, };

    protected LearningPathRecommendationService(CompetencyRelationRepository competencyRelationRepository, LearningObjectService learningObjectService,
            ParticipantScoreService participantScoreService) {
        this.competencyRelationRepository = competencyRelationRepository;
        this.learningObjectService = learningObjectService;
        this.participantScoreService = participantScoreService;
    }

    /**
     * Analyzes the current progress within the learning path and generates a recommended ordering of competencies.
     *
     * @param learningPath the learning path that should be analyzed
     * @return the state of the simulation including the recommended ordering of competencies
     */
    public RecommendationState getRecommendedOrderOfCompetencies(LearningPath learningPath) {
        RecommendationState state = generateInitialRecommendationState(learningPath);
        var pendingCompetencies = getPendingCompetencies(learningPath.getCompetencies(), state);
        simulateProgression(pendingCompetencies, state);
        return state;
    }

    /**
     * Generates the initial state of the recommendation containing all necessary information for the prediction.
     *
     * @param learningPath the learning path that should be analyzed
     * @return the initial RecommendationState
     * @see RecommendationState
     */
    private RecommendationState generateInitialRecommendationState(LearningPath learningPath) {
        Map<Long, Competency> competencyIdMap = learningPath.getCompetencies().stream().collect(Collectors.toMap(Competency::getId, Function.identity()));
        Map<Long, Set<Long>> matchingClusters = getMatchingCompetencyClusters(learningPath.getCompetencies());
        Map<Long, Set<Long>> priorsCompetencies = getPriorCompetencyMapping(learningPath.getCompetencies(), matchingClusters);
        Map<Long, Long> extendsCompetencies = getExtendsCompetencyMapping(learningPath.getCompetencies(), matchingClusters, priorsCompetencies);
        Map<Long, Long> assumesCompetencies = getAssumesCompetencyMapping(learningPath.getCompetencies(), matchingClusters, priorsCompetencies);
        Set<Long> masteredCompetencies = new HashSet<>();
        // map of non-mastered competencies to their normalized mastery score with respect to the associated threshold
        Map<Long, Double> competencyMastery = new HashMap<>();
        learningPath.getCompetencies().forEach(competency -> {
            // fetched learning path only contains data of the associated user
            final var progress = competency.getUserProgress().stream().findFirst();
            if (progress.isEmpty()) {
                competencyMastery.put(competency.getId(), 0d);
            }
            else if (CompetencyProgressService.isMastered(progress.get())) {
                // add competency to mastered set if mastered
                masteredCompetencies.add(competency.getId());
            }
            else {
                // calculate mastery progress if not completed yet
                competencyMastery.put(competency.getId(), CompetencyProgressService.getMasteryProgress(progress.get()));
            }
        });
        return new RecommendationState(competencyIdMap, new ArrayList<>(), masteredCompetencies, competencyMastery, matchingClusters, priorsCompetencies, extendsCompetencies,
                assumesCompetencies);
    }

    /**
     * Gets a map from competency ids to a set of all other competency ids that are connected via matching relations (transitive closure, including the competency itself).
     *
     * @param competencies the competencies for which the mapping should be generated
     * @return map representing the matching clusters
     */
    private Map<Long, Set<Long>> getMatchingCompetencyClusters(Set<Competency> competencies) {
        final Map<Long, Set<Long>> matchingClusters = new HashMap<>();
        for (var competency : competencies) {
            if (!matchingClusters.containsKey(competency.getId())) {
                final var matchingCompetencies = competencyRelationRepository.getMatchingCompetenciesByCompetencyId(competency.getId());
                // add for each in cluster to reduce database calls (once per cluster)
                matchingCompetencies.forEach(id -> matchingClusters.put(id, matchingCompetencies));
            }
        }
        return matchingClusters;
    }

    /**
     * Gets a map from competency ids to a set of all other competency ids that are connected via a non-matching relation.
     *
     * @param competencies     the competencies for which the mapping should be generated
     * @param matchingClusters the map representing the corresponding matching clusters
     * @return map to retrieve prior competencies
     */
    private Map<Long, Set<Long>> getPriorCompetencyMapping(Set<Competency> competencies, Map<Long, Set<Long>> matchingClusters) {
        Map<Long, Set<Long>> priorsMap = new HashMap<>();
        for (var competency : competencies) {
            if (!priorsMap.containsKey(competency.getId())) {
                final var priors = competencyRelationRepository.getPriorCompetenciesByCompetencyIds(matchingClusters.get(competency.getId()));
                // add for each in cluster to reduce database calls (once per cluster)
                matchingClusters.get(competency.getId()).forEach(id -> priorsMap.put(id, priors));
            }
        }
        return priorsMap;
    }

    /**
     * Gets a map from competency ids to number of competencies that the corresponding competency extends.
     *
     * @param competencies      the competencies for which the mapping should be generated
     * @param matchingClusters  the map representing the corresponding matching clusters
     * @param priorCompetencies the map to retrieve corresponding prior competencies
     * @return map to retrieve the number of competencies a competency extends
     */
    private Map<Long, Long> getExtendsCompetencyMapping(Set<Competency> competencies, Map<Long, Set<Long>> matchingClusters, Map<Long, Set<Long>> priorCompetencies) {
        return getRelationsOfTypeCompetencyMapping(competencies, matchingClusters, priorCompetencies, RelationType.EXTENDS);
    }

    /**
     * Gets a map from competency ids to number of competencies that the corresponding competency assumes.
     *
     * @param competencies      the competencies for which the mapping should be generated
     * @param matchingClusters  the map representing the corresponding matching clusters
     * @param priorCompetencies the map to retrieve corresponding prior competencies
     * @return map to retrieve the number of competencies a competency assumes
     */
    private Map<Long, Long> getAssumesCompetencyMapping(Set<Competency> competencies, Map<Long, Set<Long>> matchingClusters, Map<Long, Set<Long>> priorCompetencies) {
        return getRelationsOfTypeCompetencyMapping(competencies, matchingClusters, priorCompetencies, RelationType.ASSUMES);
    }

    /**
     * Gets a map from competency ids to number of competencies that the corresponding competency relates to with the specified type.
     *
     * @param competencies      the competencies for which the mapping should be generated
     * @param matchingClusters  the map representing the corresponding matching clusters
     * @param priorCompetencies the map to retrieve corresponding prior competencies
     * @param type              the relation type that should be counted
     * @return map to retrieve the number of competencies a competency extends
     */
    private Map<Long, Long> getRelationsOfTypeCompetencyMapping(Set<Competency> competencies, Map<Long, Set<Long>> matchingClusters, Map<Long, Set<Long>> priorCompetencies,
            RelationType type) {
        Map<Long, Long> map = new HashMap<>();
        for (var competency : competencies) {
            if (!map.containsKey(competency.getId())) {
                long numberOfRelations = competencyRelationRepository.countRelationsOfTypeBetweenCompetencyGroups(matchingClusters.get(competency.getId()), type,
                        priorCompetencies.get(competency.getId()));
                // add for each in cluster to reduce database calls (once per cluster)
                matchingClusters.get(competency.getId()).forEach(id -> map.put(id, numberOfRelations));
            }
        }
        return map;
    }

    /**
     * Gets the set of competencies that are themselves not mastered and no matching competency is mastered.
     *
     * @param competencies the set of competencies that should be filtered
     * @param state        the current state of the recommendation system
     * @return set of pending competencies
     */
    private Set<Competency> getPendingCompetencies(Set<Competency> competencies, RecommendationState state) {
        Set<Competency> pendingCompetencies = new HashSet<>(competencies);
        pendingCompetencies.removeIf(competency -> state.masteredCompetencies.contains(competency.getId())
                || state.matchingClusters.get(competency.getId()).stream().anyMatch(state.masteredCompetencies::contains));
        return pendingCompetencies;
    }

    /**
     * Generates a recommended ordering of competencies.
     *
     * @param pendingCompetencies the set of pending competencies
     * @param state               the current state of the recommendation system
     */
    private void simulateProgression(Set<Competency> pendingCompetencies, RecommendationState state) {
        while (!pendingCompetencies.isEmpty()) {
            Map<Long, Double> utilities = computeUtilities(pendingCompetencies, state);
            var maxEntry = utilities.entrySet().stream().max(Comparator.comparingDouble(Map.Entry::getValue));
            // is present since outstandingCompetencies is not empty
            Long competencyId = maxEntry.get().getKey();

            // add competency to recommended order
            state.recommendedOrderOfCompetencies.add(competencyId);

            // simulate completion of competency
            state.masteredCompetencies.add(competencyId);
            pendingCompetencies
                    .removeIf(competency -> competency.getId().equals(competencyId) || state.matchingClusters.get(competency.getId()).stream().anyMatch(competencyId::equals));
        }
    }

    /**
     * Generates a mapping from competency ids to their corresponding utility in the current state.
     *
     * @param competencies the set of competencies for which the mapping should be generated
     * @param state        the current state of the recommendation system
     * @return map to retrieve the utility of a competency
     */
    private Map<Long, Double> computeUtilities(Set<Competency> competencies, RecommendationState state) {
        Map<Long, Double> utilities = new HashMap<>();
        for (var competency : competencies) {
            utilities.put(competency.getId(), computeUtilityOfCompetency(competency, state));
        }
        return utilities;
    }

    /**
     * Gets the utility of a competency in the current state.
     *
     * @param competency the competency for which the utility should be computed
     * @param state      the current state of the recommendation system
     * @return the utility of the given competency
     */
    private double computeUtilityOfCompetency(Competency competency, RecommendationState state) {
        // if competency is already mastered there competency has no utility
        if (state.masteredCompetencies.contains(competency.getId())) {
            return 0;
        }
        double utility = 0;
        utility += computeDueDateUtility(competency);
        utility += computePriorUtility(competency, state);
        utility += computeExtendsOrAssumesUtility(competency, state);
        utility += computeMasteryUtility(competency, state);
        return utility;
    }

    /**
     * Gets the utility of the competency with respect to the earliest due date of the competency.
     *
     * @param competency the competency for which the utility should be computed
     * @return due date utility of the competency
     */
    private static double computeDueDateUtility(Competency competency) {
        final var earliestDueDate = getEarliestDueDate(competency);
        if (earliestDueDate.isEmpty()) {
            return 0;
        }
        double timeDelta = ChronoUnit.DAYS.between(ZonedDateTime.now(), earliestDueDate.get());

        if (timeDelta < 0) {
            // deadline has passed
            return (-timeDelta) * DUE_DATE_UTILITY;
        }
        else if (timeDelta > 0) {
            // deadline not passed yet
            return (1 / timeDelta) * DUE_DATE_UTILITY;
        }
        else {
            return DUE_DATE_UTILITY;
        }
    }

    /**
     * Gets the earliest due date of any learning object attached to the competency or the competency itself.
     *
     * @param competency the competency for which the earliest due date should be retrieved
     * @return earliest due date of the competency
     */
    private static Optional<ZonedDateTime> getEarliestDueDate(Competency competency) {
        final var lectureDueDates = competency.getLectureUnits().stream().map(LectureUnit::getLecture).map(Lecture::getEndDate);
        final var exerciseDueDates = competency.getExercises().stream().map(Exercise::getDueDate);
        return Stream.concat(Stream.concat(Stream.of(competency.getSoftDueDate()), lectureDueDates), exerciseDueDates).filter(Objects::nonNull).min(Comparator.naturalOrder());
    }

    /**
     * Gets the utility of the competency with respect to prior competencies.
     *
     * @param competency the competency for which the utility should be computed
     * @param state      the current state of the recommendation system
     * @return prior utility of the competency
     */
    private static double computePriorUtility(Competency competency, RecommendationState state) {
        // return max utility if no prior competencies are present
        if (state.priorCompetencies.get(competency.getId()).isEmpty()) {
            return PRIOR_UTILITY;
        }
        final double masteredPriorCompetencies = state.priorCompetencies.get(competency.getId()).stream()
                .filter(id -> state.masteredCompetencies.contains(id) || state.matchingClusters.get(id).stream().anyMatch(state.masteredCompetencies::contains)).count();
        final double weight = masteredPriorCompetencies / state.priorCompetencies.get(competency.getId()).size();
        return weight * PRIOR_UTILITY;
    }

    /**
     * Gets the utility of the competency with respect to prior competencies that are extended or assumed by this competency.
     *
     * @param competency the competency for which the utility should be computed
     * @param state      the current state of the recommendation system
     * @return extends or assumes utility of the competency
     */
    private static double computeExtendsOrAssumesUtility(Competency competency, RecommendationState state) {
        final double weight = state.extendsCompetencies.get(competency.getId()) * EXTENDS_UTILITY_RATIO + state.assumesCompetencies.get(competency.getId()) * ASSUMES_UTILITY_RATIO;
        // return max utility if competency does not extend or assume other competencies
        if (weight == 0) {
            return EXTENDS_OR_ASSUMES_UTILITY;
        }
        return (1 / weight) * EXTENDS_OR_ASSUMES_UTILITY;

    }

    /**
     * Gets the utility of the competency with respect to users mastery progress within the competency.
     *
     * @param competency the competency for which the utility should be computed
     * @param state      the current state of the recommendation system
     * @return mastery utility of the competency
     */
    private static double computeMasteryUtility(Competency competency, RecommendationState state) {
        return state.competencyMastery.get(competency.getId()) * MASTERY_PROGRESS_UTILITY;
    }

    /**
     * Analyzes the current progress within the learning path and generates a recommended ordering of learning objects in a competency.
     *
     * @param learningPath              the learning path that should be analyzed
     * @param competency                the competency
     * @param state                     the current state of the recommendation
     * @param alreadyScheduledExercises the exercises that have already been scheduled
     * @return the recommended ordering of learning objects
     */
    public List<LearningObject> getRecommendedOrderOfLearningObjects(LearningPath learningPath, Competency competency, RecommendationState state,
            Set<Exercise> alreadyScheduledExercises) {
        var pendingLectureUnits = competency.getLectureUnits().stream().filter(lectureUnit -> !lectureUnit.isCompletedFor(learningPath.getUser())).toList();
        List<LearningObject> recommendedOrder = new ArrayList<>(pendingLectureUnits);

        // early return if competency can be trivially mastered
        if (CompetencyProgressService.canBeMasteredWithoutExercises(competency)) {
            return recommendedOrder;
        }

        final var combinedPriorConfidence = computeCombinedPriorConfidence(competency, state);
        final var pendingExercises = competency.getExercises().stream().filter(exercise -> !learningObjectService.isCompletedByUser(exercise, learningPath.getUser()))
                .collect(Collectors.toSet());
        final var numberOfExercisesRequiredToMaster = predictNumberOfExercisesRequiredToMaster(learningPath, competency, combinedPriorConfidence, pendingExercises.size());
        Map<DifficultyLevel, Set<Exercise>> difficultyLevelMap = generateDifficultyLevelMap(competency.getExercises());
        if (numberOfExercisesRequiredToMaster >= competency.getExercises().size()) {
            scheduleAllExercises(recommendedOrder, difficultyLevelMap, alreadyScheduledExercises);
            return recommendedOrder;
        }
        final var recommendedExerciseDistribution = getRecommendedExerciseDistribution(numberOfExercisesRequiredToMaster, combinedPriorConfidence);
        if (Arrays.stream(recommendedExerciseDistribution).sum() >= competency.getExercises().size()) {
            // The calculation of the distribution uses the ceiling of the recommendation to schedule sufficiently many exercises required for mastery.
            // For competencies with only few exercises, this might cause the number of recommended exercises to surpass the number of linked exercises.
            scheduleAllExercises(recommendedOrder, difficultyLevelMap, alreadyScheduledExercises);
            return recommendedOrder;
        }

        scheduleExercisesByDistribution(recommendedOrder, recommendedExerciseDistribution, learningPath, competency, alreadyScheduledExercises);
        return recommendedOrder;
    }

    /**
     * Adds all exercises of the given difficulty map to the recommended order of learning objects.
     *
     * @param recommendedOrder          the list storing the recommended order of learning objects
     * @param difficultyLevelMap        a map from difficulty level to a set of corresponding exercises
     * @param alreadyScheduledExercises the exercises that have already been scheduled
     */
    private void scheduleAllExercises(List<LearningObject> recommendedOrder, Map<DifficultyLevel, Set<Exercise>> difficultyLevelMap, Set<Exercise> alreadyScheduledExercises) {
        for (var difficulty : DifficultyLevel.values()) {
            var unscheduledExercises = difficultyLevelMap.get(difficulty).stream().filter(exercise -> !alreadyScheduledExercises.contains(exercise)).toList();
            alreadyScheduledExercises.addAll(unscheduledExercises);
            recommendedOrder.addAll(unscheduledExercises);
        }
    }

    /**
     * Adds exercises to the recommended order of learning objects according to the given distribution.
     *
     * @param recommendedOrder                 the list storing the recommended order of learning objects
     * @param recommendedExercisesDistribution an array containing the number of exercises that should be scheduled per difficulty (easy to hard)
     * @param learningPath                     the learning path for which the recommendation should be performed
     * @param competency                       the competency from which the exercises should be chosen
     * @param alreadyScheduledExercises        the exercises that have already been scheduled
     */
    private void scheduleExercisesByDistribution(List<LearningObject> recommendedOrder, int[] recommendedExercisesDistribution, LearningPath learningPath, Competency competency,
            Set<Exercise> alreadyScheduledExercises) {
        var exerciseCandidates = competency.getExercises().stream().filter(exercise -> !hasScoredAtLeast(exercise, learningPath.getUser(), SCORE_THRESHOLD))
                .collect(Collectors.toSet());
        final var difficultyMap = generateDifficultyLevelMap(exerciseCandidates);
        final var easyExercises = new HashSet<Exercise>();
        final var mediumExercises = new HashSet<Exercise>();
        final var hardExercises = new HashSet<Exercise>();

        // choose as many exercises from the correct difficulty level as possible
        final var missingEasy = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.EASY, recommendedExercisesDistribution[0], easyExercises);
        final var missingMedium = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.MEDIUM, recommendedExercisesDistribution[1], mediumExercises);
        final var missingHard = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.HARD, recommendedExercisesDistribution[2], hardExercises);
        int numberOfMissingExercises = missingEasy + missingMedium + missingHard;

        // if there are not sufficiently many exercises per difficulty level, prefer medium difficulty
        // case 1: no medium exercises available/medium exercises missing: continue to fill with easy/hard exercises
        // case 2: medium exercises available: no medium exercises missing -> missing exercises must be easy/hard -> in both scenarios medium is the closest difficulty level
        if (numberOfMissingExercises > 0 && !difficultyMap.get(DifficultyLevel.MEDIUM).isEmpty()) {
            numberOfMissingExercises = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.MEDIUM, numberOfMissingExercises, mediumExercises);
        }

        // if there are still not sufficiently many medium exercises, choose easy difficulty
        // prefer easy to hard exercises to avoid student overload
        if (numberOfMissingExercises > 0 && !difficultyMap.get(DifficultyLevel.EASY).isEmpty()) {
            numberOfMissingExercises = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.EASY, numberOfMissingExercises, easyExercises);
        }

        // fill remaining slots with hard difficulty
        if (numberOfMissingExercises > 0) {
            selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.HARD, numberOfMissingExercises, hardExercises);
        }

        easyExercises.removeIf(alreadyScheduledExercises::contains);
        mediumExercises.removeIf(alreadyScheduledExercises::contains);
        hardExercises.removeIf(alreadyScheduledExercises::contains);

        alreadyScheduledExercises.addAll(easyExercises);
        alreadyScheduledExercises.addAll(mediumExercises);
        alreadyScheduledExercises.addAll(hardExercises);

        recommendedOrder.addAll(easyExercises);
        recommendedOrder.addAll(mediumExercises);
        recommendedOrder.addAll(hardExercises);
    }

    /**
     * Selects a given number of exercises of specified difficulty.
     * <p>
     * If there are not sufficiently exercises available, the method returns the number of exercises that could not be selected with the particular difficulty.
     *
     * @param difficultyMap     a map from difficulty level to a set of corresponding exercises
     * @param difficulty        the difficulty level that should be chosen
     * @param numberOfExercises the number of exercises that should be selected
     * @param exercises         the set to store the selected exercises
     * @return number of exercises that could not be selected
     */
    private static int selectExercisesWithDifficulty(Map<DifficultyLevel, Set<Exercise>> difficultyMap, DifficultyLevel difficulty, int numberOfExercises,
            Set<Exercise> exercises) {
        var selectedExercises = difficultyMap.get(difficulty).stream().limit(numberOfExercises).collect(Collectors.toSet());
        exercises.addAll(selectedExercises);
        difficultyMap.get(difficulty).removeAll(selectedExercises);
        return numberOfExercises - selectedExercises.size();
    }

    /**
     * Computes the average confidence of all prior competencies.
     *
     * @param competency the competency for which the average prior confidence should be computed
     * @param state      the current state of the recommendation (containing the mapping for prior competencies)
     * @return the average confidence of all prior competencies
     */
    private static double computeCombinedPriorConfidence(Competency competency, RecommendationState state) {
        return state.priorCompetencies.get(competency.getId()).stream().map(state.competencyIdMap::get).map(c -> c.getUserProgress().stream().findFirst())
                .mapToDouble(competencyProgress -> {
                    if (competencyProgress.isEmpty()) {
                        return 0;
                    }
                    return competencyProgress.get().getConfidence();
                }).sorted().average().orElse(100);
    }

    /**
     * Predicts the number of exercises required to master the given competency based on prior performance and current progress.
     * <p>
     * The following formulas are used predict the number of exercises required to master the competency:
     * <ul>
     * <li>Mastery >= MasteryThreshold</li>
     * <li>Mastery = 1/3 * Progress + 2/3 * Confidence</li>
     * <li>Progress = (#CompletedLearningObjects + #ExercisesRequiredToMaster) / #LearningObjects</li>
     * <li>Confidence = (Sum of LatestScores + #ExercisesRequiredToMaster * avg. prior Confidence) / (#LatestScores + #ExercisesRequiredToMaster)</li>
     * </ul>
     * The formulas are substituted and solved for #ExercisesRequiredToMaster.
     *
     * @param learningPath             the learning path for which the prediction should be computed
     * @param competency               the competency for which the prediction should be computed
     * @param priorConfidence          the average confidence of all prior competencies
     * @param numberOfPendingExercises the number of exercises that have not been completed by the user
     * @return the predicted number of exercises required to master the given competency
     */
    private int predictNumberOfExercisesRequiredToMaster(LearningPath learningPath, Competency competency, double priorConfidence, int numberOfPendingExercises) {
        // we assume that the student may perform slightly worse that previously and dampen the prior confidence for the prediction process
        priorConfidence *= 0.75;
        final var scores = participantScoreService.getStudentAndTeamParticipationScoresAsDoubleStream(learningPath.getUser(), competency.getExercises()).summaryStatistics();
        double lectureUnits = competency.getLectureUnits().size();
        double exercises = competency.getExercises().size();
        double learningObjects = lectureUnits + exercises;
        double masteryThreshold = competency.getMasteryThreshold();
        double completedExercises = exercises - numberOfPendingExercises;
        double a = 100d / (3d * learningObjects);
        double b = 100d * (lectureUnits + completedExercises + scores.getCount()) / (3d * learningObjects) + 2d * priorConfidence / 3d - masteryThreshold;
        double c = 100d * (lectureUnits + completedExercises) * scores.getCount() / (3d * learningObjects) + 2d * scores.getSum() / 3d - masteryThreshold * scores.getCount();
        double D = Math.sqrt(Math.pow(b, 2) - 4 * a * c);
        double prediction1 = Math.ceil((-b + D) / (2d * a));
        double prediction2 = Math.ceil((-b - D) / (2d * a));
        int prediction = (int) Math.max(prediction1, prediction2);
        // numerical edge case, can't happen for valid competencies
        return Math.max(prediction, 0);
    }

    /**
     * Generates a map from difficulty level to a set of corresponding exercises.
     *
     * @param exercises the exercises that should be contained in the map
     * @return a map from difficulty level to a set of corresponding exercises
     */
    private static Map<DifficultyLevel, Set<Exercise>> generateDifficultyLevelMap(Set<Exercise> exercises) {
        Map<DifficultyLevel, Set<Exercise>> difficultyLevelMap = new HashMap<>();
        for (var difficulty : DifficultyLevel.values()) {
            difficultyLevelMap.put(difficulty, new HashSet<>());
        }

        exercises.forEach(exercise -> {
            var difficulty = exercise.getDifficulty();
            // if no difficulty is set, assume medium difficulty level
            if (difficulty == null) {
                difficulty = DifficultyLevel.MEDIUM;
            }
            difficultyLevelMap.get(difficulty).add(exercise);
        });

        return difficultyLevelMap;
    }

    /**
     * Computes the recommended amount of exercises per difficulty level.
     *
     * @param numberOfExercisesRequiredToMaster the minimum number of exercises that should be recommended
     * @param priorConfidence                   the average confidence of all prior competencies
     * @return array containing the recommended number of exercises per difficulty level (easy to hard)
     */
    private static int[] getRecommendedExerciseDistribution(int numberOfExercisesRequiredToMaster, double priorConfidence) {
        final var distribution = getExerciseDifficultyDistribution(priorConfidence);
        final var numberOfExercises = new int[DifficultyLevel.values().length];
        for (int i = 0; i < numberOfExercises.length; i++) {
            numberOfExercises[i] = (int) Math.round(Math.ceil(distribution[i] * numberOfExercisesRequiredToMaster));
        }
        return numberOfExercises;
    }

    /**
     * Retrieves the corresponding distribution from the lookup table.
     *
     * @param priorConfidence the median of the normal distribution
     * @return array containing the distribution in percent per difficulty level (easy to hard)
     */
    private static double[] getExerciseDifficultyDistribution(double priorConfidence) {
        int distributionIndex = (int) Math.round(priorConfidence * (EXERCISE_DIFFICULTY_DISTRIBUTION_LUT.length - 1) / 100);
        return EXERCISE_DIFFICULTY_DISTRIBUTION_LUT[distributionIndex];
    }

    public record RecommendationState(Map<Long, Competency> competencyIdMap, List<Long> recommendedOrderOfCompetencies, Set<Long> masteredCompetencies,
            Map<Long, Double> competencyMastery, Map<Long, Set<Long>> matchingClusters, Map<Long, Set<Long>> priorCompetencies, Map<Long, Long> extendsCompetencies,
            Map<Long, Long> assumesCompetencies) {
    }

    /**
     * Checks if the user has achieved the minimum score.
     *
     * @param exercise the exercise that should be checked
     * @param user     the user for which to check the score
     * @param minScore the minimum score that should be achieved
     * @return true if the user achieved the minimum score, false otherwise
     */
    private boolean hasScoredAtLeast(@NotNull Exercise exercise, @NotNull User user, double minScore) {
        final var score = participantScoreService.getStudentAndTeamParticipationScoresAsDoubleStream(user, Set.of(exercise)).max();
        if (score.isEmpty()) {
            return false;
        }
        return score.getAsDouble() >= minScore;
    }
}
