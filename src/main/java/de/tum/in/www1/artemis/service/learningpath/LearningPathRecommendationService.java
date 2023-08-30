package de.tum.in.www1.artemis.service.learningpath;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LearningObject;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.CompetencyRelationRepository;
import de.tum.in.www1.artemis.service.*;

/**
 * Service Implementation for the recommendation of competencies and learning objects in learning paths.
 */
@Service
public class LearningPathRecommendationService {

    private final Logger log = LoggerFactory.getLogger(LearningPathRecommendationService.class);

    private final CompetencyRelationRepository competencyRelationRepository;

    private final LearningObjectService learningObjectService;

    private final ExerciseService exerciseService;

    private final ParticipantScoreService participantScoreService;

    private final static double DUE_DATE_UTILITY = 10;

    private final static double PRIOR_UTILITY = 150;

    // Important: EXTENDS_UTILITY should be smaller than ASSUMES_UTILITY to prefer extends-relation to assumes-relations.
    private final static double EXTENDS_UTILITY_RATIO = 1;

    private final static double ASSUMES_UTILITY_RATIO = 2;

    private final static double EXTENDS_OR_ASSUMES_UTILITY = 100;

    private final static double MASTERY_PROGRESS_UTILITY = 1;

    private final static double SCORE_THRESHOLD = 50;

    /**
     * LUT containing the distribution of exercises by difficulty level that should be recommended.
     * <p>
     * Values can be reproduced by computing the cumulative normal distribution function (cdf) for the general normal distribution f(x|mean=[0.00...1.00], std_dev=0.35): {easy:
     * cdf(0.40), medium: cdf(0.85) - cdf(0.40), hard: 1 - cdf(0.85)}.
     * Each array corresponds to the mean=idx/#distributions.
     */
    private final static double[][] EXERCISE_DIFFICULTY_DISTRIBUTION_LUT = new double[][] { { 0.87, 0.12, 0.01 }, { 0.80, 0.18, 0.02 }, { 0.72, 0.25, 0.03 }, { 0.61, 0.33, 0.06 },
            { 0.50, 0.40, 0.10 }, { 0.39, 0.45, 0.16 }, { 0.28, 0.48, 0.24 }, { 0.20, 0.47, 0.33 }, { 0.13, 0.43, 0.44 }, { 0.08, 0.37, 0.55 }, { 0.04, 0.29, 0.67 }, };

    protected LearningPathRecommendationService(CompetencyRelationRepository competencyRelationRepository, LearningObjectService learningObjectService,
            ExerciseService exerciseService, ParticipantScoreService participantScoreService) {
        this.competencyRelationRepository = competencyRelationRepository;
        this.learningObjectService = learningObjectService;
        this.exerciseService = exerciseService;
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
        HashMap<Long, Competency> competencyIdMap = (HashMap<Long, Competency>) learningPath.getCompetencies().stream()
                .collect(Collectors.toMap(Competency::getId, Function.identity()));
        HashMap<Long, Set<Long>> matchingClusters = getMatchingCompetencyClusters(learningPath.getCompetencies());
        HashMap<Long, Set<Long>> priorsCompetencies = getPriorCompetencyMapping(learningPath.getCompetencies(), matchingClusters);
        HashMap<Long, Long> extendsCompetencies = getExtendsCompetencyMapping(learningPath.getCompetencies(), matchingClusters, priorsCompetencies);
        HashMap<Long, Long> assumesCompetencies = getAssumesCompetencyMapping(learningPath.getCompetencies(), matchingClusters, priorsCompetencies);
        Set<Long> masteredCompetencies = new HashSet<>();
        // map of non-mastered competencies to their normalized mastery score with respect to the associated threshold
        HashMap<Long, Double> competencyMastery = new HashMap<>();
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
    private HashMap<Long, Set<Long>> getMatchingCompetencyClusters(Set<Competency> competencies) {
        final HashMap<Long, Set<Long>> matchingClusters = new HashMap<>();
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
    private HashMap<Long, Set<Long>> getPriorCompetencyMapping(Set<Competency> competencies, HashMap<Long, Set<Long>> matchingClusters) {
        HashMap<Long, Set<Long>> priorsMap = new HashMap<>();
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
    private HashMap<Long, Long> getExtendsCompetencyMapping(Set<Competency> competencies, HashMap<Long, Set<Long>> matchingClusters, HashMap<Long, Set<Long>> priorCompetencies) {
        return getRelationsOfTypeCompetencyMapping(competencies, matchingClusters, priorCompetencies, CompetencyRelation.RelationType.EXTENDS);
    }

    /**
     * Gets a map from competency ids to number of competencies that the corresponding competency assumes.
     *
     * @param competencies      the competencies for which the mapping should be generated
     * @param matchingClusters  the map representing the corresponding matching clusters
     * @param priorCompetencies the map to retrieve corresponding prior competencies
     * @return map to retrieve the number of competencies a competency assumes
     */
    private HashMap<Long, Long> getAssumesCompetencyMapping(Set<Competency> competencies, HashMap<Long, Set<Long>> matchingClusters, HashMap<Long, Set<Long>> priorCompetencies) {
        return getRelationsOfTypeCompetencyMapping(competencies, matchingClusters, priorCompetencies, CompetencyRelation.RelationType.ASSUMES);
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
    private HashMap<Long, Long> getRelationsOfTypeCompetencyMapping(Set<Competency> competencies, HashMap<Long, Set<Long>> matchingClusters,
            HashMap<Long, Set<Long>> priorCompetencies, CompetencyRelation.RelationType type) {
        HashMap<Long, Long> map = new HashMap<>();
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
            HashMap<Long, Double> utilities = computeUtilities(pendingCompetencies, state);
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
    private HashMap<Long, Double> computeUtilities(Set<Competency> competencies, RecommendationState state) {
        HashMap<Long, Double> utilities = new HashMap<>();
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
        if (state.priorCompetencies.get(competency.getId()).size() == 0) {
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
     * @param learningPath the learning path that should be analyzed
     * @param competency   the competency
     * @param state        the current state of the recommendation
     * @return the recommended ordering of learning objects
     */
    public List<LearningObject> getRecommendedOrderOfLearningObjects(LearningPath learningPath, Competency competency, RecommendationState state) {
        var pendingLectureUnits = competency.getLectureUnits().stream().filter(lectureUnit -> !lectureUnit.isCompletedFor(learningPath.getUser())).toList();
        ArrayList<LearningObject> recommendedOrder = new ArrayList<>(pendingLectureUnits);

        // early return if competency can be trivially mastered
        if (canBeMasteredWithoutExercises(competency)) {
            return recommendedOrder;
        }

        final var combinedPriorConfidence = computeCombinedPriorConfidence(competency, state);
        final var pendingExercises = competency.getExercises().stream().filter(exercise -> !learningObjectService.isCompletedByUser(exercise, learningPath.getUser()))
                .collect(Collectors.toSet());
        final var numberOfExercisesRequiredToMaster = predictNumberOfExercisesRequiredToMaster(learningPath, competency, combinedPriorConfidence, pendingExercises.size());
        log.warn("numberOfExercisesRequiredToMaster:" + numberOfExercisesRequiredToMaster);
        HashMap<DifficultyLevel, Set<Exercise>> difficultyLevelMap = generateDifficultyLevelMap(competency.getExercises());
        if (numberOfExercisesRequiredToMaster >= competency.getExercises().size()) {
            scheduleAllExercises(recommendedOrder, difficultyLevelMap);
            return recommendedOrder;
        }
        final var recommendedExerciseDistribution = getRecommendedExerciseDistribution(numberOfExercisesRequiredToMaster, combinedPriorConfidence);
        if (Arrays.stream(recommendedExerciseDistribution).sum() >= competency.getExercises().size()) {
            scheduleAllExercises(recommendedOrder, difficultyLevelMap);
            return recommendedOrder;
        }

        scheduleExercisesByDistribution(recommendedOrder, recommendedExerciseDistribution, learningPath, competency);
        return recommendedOrder;
    }

    /**
     * Checks if the competency can be mastered without completing any exercises.
     *
     * @param competency the competency to check
     * @return true if the competency can be mastered without completing any exercises, false otherwise
     */
    private static boolean canBeMasteredWithoutExercises(@NotNull Competency competency) {
        return ((double) competency.getLectureUnits().size()) / (3 * (competency.getLectureUnits().size() + competency.getExercises().size())) * 100 >= competency
                .getMasteryThreshold();
    }

    private void scheduleAllExercises(ArrayList<LearningObject> recommendedOrder, HashMap<DifficultyLevel, Set<Exercise>> difficultyLevelMap) {
        for (var difficulty : DifficultyLevel.values()) {
            recommendedOrder.addAll(difficultyLevelMap.get(difficulty));
        }
    }

    private void scheduleExercisesByDistribution(ArrayList<LearningObject> recommendedOrder, int[] recommendedExercisesDistribution, LearningPath learningPath,
            Competency competency) {
        var exerciseCandidates = competency.getExercises().stream().filter(exercise -> !exerciseService.hasScoredAtLeast(exercise, learningPath.getUser(), SCORE_THRESHOLD))
                .collect(Collectors.toSet());
        final var difficultyMap = generateDifficultyLevelMap(exerciseCandidates);
        final var easyExercises = new HashSet<Exercise>();
        final var mediumExercises = new HashSet<Exercise>();
        final var hardExercises = new HashSet<Exercise>();

        // choose as many exercises from the correct difficulty level as possible
        final var missingEasy = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.EASY, recommendedExercisesDistribution[0], easyExercises);
        final var missingMedium = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.MEDIUM, recommendedExercisesDistribution[1], easyExercises);
        final var missingHard = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.HARD, recommendedExercisesDistribution[2], easyExercises);
        int numberOfMissingExercises = missingEasy + missingMedium + missingHard;

        // if there are not sufficiently many exercises per difficulty level, prefer medium difficulty
        if (numberOfMissingExercises > 0 && !difficultyMap.get(DifficultyLevel.MEDIUM).isEmpty()) {
            numberOfMissingExercises = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.MEDIUM, numberOfMissingExercises, mediumExercises);
        }

        // if there are still not sufficiently many medium exercises, choose easy difficulty
        if (numberOfMissingExercises > 0 && !difficultyMap.get(DifficultyLevel.EASY).isEmpty()) {
            numberOfMissingExercises = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.EASY, numberOfMissingExercises, easyExercises);
        }

        // fill remaining slots with hard difficulty
        if (numberOfMissingExercises > 0) {
            selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.HARD, numberOfMissingExercises, hardExercises);
        }

        recommendedOrder.addAll(easyExercises);
        recommendedOrder.addAll(mediumExercises);
        recommendedOrder.addAll(hardExercises);
    }

    private static int selectExercisesWithDifficulty(HashMap<DifficultyLevel, Set<Exercise>> difficultyMap, DifficultyLevel difficulty, int numberOfExercises,
            Set<Exercise> exercises) {
        var selectedExercises = difficultyMap.get(difficulty).stream().limit(numberOfExercises).collect(Collectors.toSet());
        exercises.addAll(selectedExercises);
        difficultyMap.get(difficulty).removeAll(selectedExercises);
        return numberOfExercises - selectedExercises.size();
    }

    private static double computeCombinedPriorConfidence(Competency competency, RecommendationState state) {
        return state.priorCompetencies.get(competency.getId()).stream().map(state.competencyIdMap::get).map(c -> c.getUserProgress().stream().findFirst())
                .mapToDouble(competencyProgress -> {
                    if (competencyProgress.isEmpty()) {
                        return 0;
                    }
                    return competencyProgress.get().getConfidence();
                }).sorted().average().orElse(100);
    }

    private int predictNumberOfExercisesRequiredToMaster(LearningPath learningPath, Competency competency, double priorConfidence, int numberOfPendingExercises) {
        final var scores = participantScoreService.getStudentAndTeamParticipationScoresAsDoubleStream(learningPath.getUser(), competency.getExercises()).summaryStatistics();
        double LU = competency.getLectureUnits().size();
        double EX = competency.getExercises().size();
        double LO = LU + EX;
        double MT = competency.getMasteryThreshold();
        double EXcomp = EX - numberOfPendingExercises;
        double a = 100d / (3d * LO);
        double b = 100d * (LU + EXcomp + scores.getCount()) / (3d * LO) + 2d * priorConfidence / 3d - MT;
        double c = 100d * (LU + EXcomp) * scores.getCount() / (3d * LO) + 2d * scores.getSum() / 3d - MT * scores.getCount();
        double D = Math.sqrt(Math.pow(b, 2) - 4 * a * c);
        double prediction1 = (-b + D) / (2d * a);
        double prediction2 = (-b - D) / (2d * a);
        int prediction = (int) Math.round(Math.max(prediction1, prediction2));
        // numerical edge case, can't happen for valid competencies
        return Math.max(prediction, 0);
    }

    private static HashMap<DifficultyLevel, Set<Exercise>> generateDifficultyLevelMap(Set<Exercise> exercises) {
        HashMap<DifficultyLevel, Set<Exercise>> difficultyLevelMap = new HashMap<>();
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

    private static int[] getRecommendedExerciseDistribution(int numberOfExercisesRequiredToMaster, double priorConfidence) {
        final var distribution = getExerciseDifficultyDistribution(priorConfidence);
        final var numberOfExercises = new int[3];
        for (int i = 0; i < numberOfExercises.length; i++) {
            numberOfExercises[i] = (int) Math.round(Math.ceil(distribution[i] * numberOfExercisesRequiredToMaster));
        }
        return numberOfExercises;
    }

    private static double[] getExerciseDifficultyDistribution(double priorConfidence) {
        int distributionIndex = (int) Math.round(priorConfidence * (EXERCISE_DIFFICULTY_DISTRIBUTION_LUT.length - 1) / 100);
        return EXERCISE_DIFFICULTY_DISTRIBUTION_LUT[distributionIndex];
    }

    protected record RecommendationState(HashMap<Long, Competency> competencyIdMap, List<Long> recommendedOrderOfCompetencies, Set<Long> masteredCompetencies,
            HashMap<Long, Double> competencyMastery, HashMap<Long, Set<Long>> matchingClusters, HashMap<Long, Set<Long>> priorCompetencies, HashMap<Long, Long> extendsCompetencies,
            HashMap<Long, Long> assumesCompetencies) {
    }
}
