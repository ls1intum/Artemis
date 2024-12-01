package de.tum.cit.aet.artemis.atlas.service.learningpath;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.AtomicDouble;

import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreService;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.BaseExercise;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.service.LearningObjectService;

/**
 * Service Implementation for the recommendation of competencies and learning objects in learning paths.
 */
@Profile(PROFILE_CORE)
@Service
public class LearningPathRecommendationService {

    private final CompetencyRelationRepository competencyRelationRepository;

    private final LearningObjectService learningObjectService;

    private final ParticipantScoreService participantScoreService;

    private final CompetencyProgressRepository competencyProgressRepository;

    private final CourseCompetencyRepository courseCompetencyRepository;

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

    private static final double PREREQUISITE_UTILITY = 200;

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
            ParticipantScoreService participantScoreService, CompetencyProgressRepository competencyProgressRepository, CourseCompetencyRepository courseCompetencyRepository) {
        this.competencyRelationRepository = competencyRelationRepository;
        this.learningObjectService = learningObjectService;
        this.participantScoreService = participantScoreService;
        this.competencyProgressRepository = competencyProgressRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
    }

    /**
     * Analyzes the current progress within the learning path and generates a recommended ordering of the not yet mastered competencies.
     *
     * @param learningPath the learning path that should be analyzed
     * @return the state of the simulation including the recommended ordering of competencies
     */
    public RecommendationState getRecommendedOrderOfNotMasteredCompetencies(LearningPath learningPath) {
        RecommendationState state = generateInitialRecommendationState(learningPath);
        var pendingCompetencies = getPendingCompetencies(learningPath.getCompetencies(), state);
        simulateProgression(pendingCompetencies, state);
        return state;
    }

    /**
     * Analyzes the current progress within the learning path and generates a recommended ordering of all competencies. The mastered competencies are at the start of the list.
     *
     * @param learningPath the learning path that should be analyzed
     * @return the state of the simulation including the recommended ordering of competencies
     */
    public RecommendationState getRecommendedOrderOfAllCompetencies(LearningPath learningPath) {
        RecommendationState state = generateInitialRecommendationState(learningPath);
        var masteredCompetencies = state.masteredCompetencies.stream().map(state.competencyIdMap::get).collect(Collectors.toSet());
        simulateProgression(masteredCompetencies, state);
        var pendingCompetencies = getPendingCompetencies(learningPath.getCompetencies(), state);
        simulateProgression(pendingCompetencies, state);
        return state;
    }

    /**
     * Gets the first learning object of a learning path
     *
     * @param user                the user that should be analyzed
     * @param recommendationState the current state of the learning path recommendation
     * @return the next due learning object of learning path
     */
    public LearningObject getFirstLearningObject(User user, RecommendationState recommendationState) {
        for (long competencyId : recommendationState.recommendedOrderOfCompetencies) {
            var competency = recommendationState.competencyIdMap.get(competencyId);
            var recommendedOrderOfLearningObjects = getRecommendedOrderOfLearningObjects(user, competency, recommendationState);
            if (!recommendedOrderOfLearningObjects.isEmpty()) {
                return recommendedOrderOfLearningObjects.getFirst();
            }
        }
        return null;
    }

    /**
     * Gets the last learning object of a learning path
     *
     * @param user                the user that should be analyzed
     * @param recommendationState the current state of the learning path recommendation
     * @return the last learning object of the learning path
     */
    public LearningObject getLastLearningObject(User user, RecommendationState recommendationState) {
        LearningObject learningObject = null;
        int indexOfLastCompletedCompetency = recommendationState.recommendedOrderOfCompetencies().size() - 1;
        while (learningObject == null && indexOfLastCompletedCompetency >= 0) {
            var lastCompletedCompetencyId = recommendationState.recommendedOrderOfCompetencies().get(indexOfLastCompletedCompetency);
            var lastCompletedCompetency = recommendationState.competencyIdMap().get(lastCompletedCompetencyId);
            var recommendedLearningObjectsInLastCompetency = getOrderOfLearningObjectsForCompetency(lastCompletedCompetency, user);
            if (!recommendedLearningObjectsInLastCompetency.isEmpty()) {
                learningObject = recommendedLearningObjectsInLastCompetency.getLast();
            }
            indexOfLastCompletedCompetency--;
        }
        return learningObject;
    }

    /**
     * Generates the initial state of the recommendation containing all necessary information for the prediction.
     *
     * @param learningPath the learning path that should be analyzed
     * @return the initial RecommendationState
     * @see RecommendationState
     */
    private RecommendationState generateInitialRecommendationState(LearningPath learningPath) {
        Map<Long, CourseCompetency> competencyIdMap = learningPath.getCompetencies().stream().collect(Collectors.toMap(CourseCompetency::getId, Function.identity()));
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
            else {
                if (CompetencyProgressService.isMastered(progress.get())) {
                    masteredCompetencies.add(competency.getId());
                }
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
    private Map<Long, Set<Long>> getMatchingCompetencyClusters(Set<CourseCompetency> competencies) {
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
    private Map<Long, Set<Long>> getPriorCompetencyMapping(Set<CourseCompetency> competencies, Map<Long, Set<Long>> matchingClusters) {
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
    private Map<Long, Long> getExtendsCompetencyMapping(Set<CourseCompetency> competencies, Map<Long, Set<Long>> matchingClusters, Map<Long, Set<Long>> priorCompetencies) {
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
    private Map<Long, Long> getAssumesCompetencyMapping(Set<CourseCompetency> competencies, Map<Long, Set<Long>> matchingClusters, Map<Long, Set<Long>> priorCompetencies) {
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
    private Map<Long, Long> getRelationsOfTypeCompetencyMapping(Set<CourseCompetency> competencies, Map<Long, Set<Long>> matchingClusters, Map<Long, Set<Long>> priorCompetencies,
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
    private Set<CourseCompetency> getPendingCompetencies(Set<CourseCompetency> competencies, RecommendationState state) {
        return competencies.stream().filter(competency -> !state.masteredCompetencies.contains(competency.getId())
                || state.matchingClusters.get(competency.getId()).stream().noneMatch(state.masteredCompetencies::contains)).collect(Collectors.toSet());
    }

    /**
     * Generates a recommended ordering of competencies.
     *
     * @param pendingCompetencies the set of pending competencies
     * @param state               the current state of the recommendation system
     */
    private void simulateProgression(Set<CourseCompetency> pendingCompetencies, RecommendationState state) {
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
    private Map<Long, Double> computeUtilities(Set<CourseCompetency> competencies, RecommendationState state) {
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
    private double computeUtilityOfCompetency(CourseCompetency competency, RecommendationState state) {
        double utility = 0;
        utility += computeDueDateUtility(competency);
        utility += computePriorUtility(competency, state);
        utility += computeExtendsOrAssumesUtility(competency, state);
        utility += computeMasteryUtility(competency, state);
        utility += computePrerequisiteUtility(competency);
        return utility;
    }

    /**
     * Gets the utility of the competency with respect to the earliest due date of the competency.
     *
     * @param competency the competency for which the utility should be computed
     * @return due date utility of the competency
     */
    private static double computeDueDateUtility(CourseCompetency competency) {
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
    private static Optional<ZonedDateTime> getEarliestDueDate(CourseCompetency competency) {
        final var lectureDueDates = competency.getLectureUnitLinks().stream().map(lectureUnitLink -> lectureUnitLink.getLectureUnit().getLecture().getEndDate());
        final var exerciseDueDates = competency.getExerciseLinks().stream().map(exerciseLink -> exerciseLink.getExercise().getDueDate());
        return Stream.concat(Stream.concat(Stream.of(competency.getSoftDueDate()), lectureDueDates), exerciseDueDates).filter(Objects::nonNull).min(Comparator.naturalOrder());
    }

    /**
     * Gets the utility of the competency with respect to prior competencies.
     *
     * @param competency the competency for which the utility should be computed
     * @param state      the current state of the recommendation system
     * @return prior utility of the competency
     */
    private static double computePriorUtility(CourseCompetency competency, RecommendationState state) {
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
    private static double computeExtendsOrAssumesUtility(CourseCompetency competency, RecommendationState state) {
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
    private static double computeMasteryUtility(CourseCompetency competency, RecommendationState state) {
        return state.competencyMastery.get(competency.getId()) * MASTERY_PROGRESS_UTILITY;
    }

    /**
     * Gets the utility of the competency with respect to it being a prerequisite or not.
     *
     * @param competency the competency for which the utility should be computed
     * @return prerequisite utility of the competency
     */
    private static double computePrerequisiteUtility(CourseCompetency competency) {
        return competency instanceof Prerequisite ? PREREQUISITE_UTILITY : 0;
    }

    /**
     * Analyzes the current progress within the learning path and generates a recommended ordering of uncompleted learning objects in a competency.
     *
     * @param user       the user that should be analyzed
     * @param competency the competency
     * @param state      the current state of the recommendation
     * @return the recommended ordering of learning objects
     */
    public List<LearningObject> getRecommendedOrderOfLearningObjects(User user, CourseCompetency competency, RecommendationState state) {
        final var combinedPriorConfidence = computeCombinedPriorConfidence(competency, state);
        return getRecommendedOrderOfLearningObjects(user, competency, combinedPriorConfidence);
    }

    /**
     * Analyzes the current progress within the learning path and generates a recommended ordering of uncompleted learning objects in a competency.
     * The ordering is based on the competency link weights in decreasing order
     *
     * @param user                    the user that should be analyzed
     * @param competency              the competency
     * @param combinedPriorConfidence the combined confidence of the user for the prior competencies
     * @return the recommended ordering of learning objects
     */
    public List<LearningObject> getRecommendedOrderOfLearningObjects(User user, CourseCompetency competency, double combinedPriorConfidence) {
        var pendingLectureUnits = competency.getLectureUnitLinks().stream().map(CompetencyLectureUnitLink::getLectureUnit).filter(lectureUnit -> !lectureUnit.isCompletedFor(user))
                .toList();
        List<LearningObject> recommendedOrder = new ArrayList<>(pendingLectureUnits);

        // early return if competency can be trivially mastered
        if (CompetencyProgressService.canBeMasteredWithoutExercises(competency)) {
            return recommendedOrder;
        }

        final var optionalCompetencyProgress = competency.getUserProgress().stream().findAny();
        final double weightedConfidence = computeWeightedConfidence(combinedPriorConfidence, optionalCompetencyProgress);

        final var numberOfRequiredExercisePointsToMaster = calculateNumberOfExercisePointsRequiredToMaster(user, competency, weightedConfidence);

        // First sort exercises based on title to ensure consistent ordering over multiple calls then prefer higher weighted exercises
        final var pendingExercises = competency.getExerciseLinks().stream().filter(link -> !learningObjectService.isCompletedByUser(link.getExercise(), user))
                .sorted(Comparator.comparing(link -> link.getExercise().getTitle())).sorted(Comparator.comparingDouble(CompetencyExerciseLink::getWeight).reversed())
                .map(CompetencyExerciseLink::getExercise).toList();

        final var pendingExercisePoints = pendingExercises.stream().mapToDouble(BaseExercise::getMaxPoints).sum();

        Map<DifficultyLevel, List<Exercise>> difficultyLevelMap = generateDifficultyLevelMap(pendingExercises);
        if (numberOfRequiredExercisePointsToMaster >= pendingExercisePoints) {
            scheduleAllExercises(recommendedOrder, difficultyLevelMap);
            return recommendedOrder;
        }
        final var recommendedExerciseDistribution = getRecommendedExercisePointDistribution(numberOfRequiredExercisePointsToMaster, weightedConfidence);

        scheduleExercisesByDistribution(recommendedOrder, recommendedExerciseDistribution, difficultyLevelMap);
        return recommendedOrder;
    }

    /**
     * Adds all exercises of the given difficulty map to the recommended order of learning objects.
     *
     * @param recommendedOrder   the list storing the recommended order of learning objects
     * @param difficultyLevelMap a map from difficulty level to a set of corresponding exercises
     */
    private void scheduleAllExercises(List<LearningObject> recommendedOrder, Map<DifficultyLevel, List<Exercise>> difficultyLevelMap) {
        for (var difficulty : DifficultyLevel.values()) {
            recommendedOrder.addAll(difficultyLevelMap.get(difficulty));
        }
    }

    /**
     * Adds exercises to the recommended order of learning objects according to the given distribution.
     *
     * @param recommendedOrder                     the list storing the recommended order of learning objects
     * @param recommendedExercisePointDistribution an array containing the number of exercise points that should be scheduled per difficulty (easy to hard)
     * @param difficultyMap                        a map from difficulty level to a set of corresponding exercises
     */
    private void scheduleExercisesByDistribution(List<LearningObject> recommendedOrder, double[] recommendedExercisePointDistribution,
            Map<DifficultyLevel, List<Exercise>> difficultyMap) {
        final var easyExercises = new ArrayList<Exercise>();
        final var mediumExercises = new ArrayList<Exercise>();
        final var hardExercises = new ArrayList<Exercise>();

        // choose as many exercises from the correct difficulty level as possible
        final var missingEasy = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.EASY, recommendedExercisePointDistribution[0], easyExercises);
        final var missingHard = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.HARD, recommendedExercisePointDistribution[2], hardExercises);

        // if there are not sufficiently many exercises per difficulty level, prefer medium difficulty
        // case 1: no medium exercises available/medium exercises missing: continue to fill with easy/hard exercises
        // case 2: medium exercises available: no medium exercises missing -> missing exercises must be easy/hard -> in both scenarios medium is the closest difficulty level
        double mediumExercisePoints = recommendedExercisePointDistribution[1] + missingEasy + missingHard;
        double numberOfMissingExercisePoints = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.MEDIUM, mediumExercisePoints, mediumExercises);

        // if there are still not sufficiently many medium exercises, choose easy difficulty
        // prefer easy to hard exercises to avoid student overload
        if (numberOfMissingExercisePoints > 0 && !difficultyMap.get(DifficultyLevel.EASY).isEmpty()) {
            numberOfMissingExercisePoints = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.EASY, numberOfMissingExercisePoints, easyExercises);
        }

        // fill remaining slots with hard difficulty
        if (numberOfMissingExercisePoints > 0 && !difficultyMap.get(DifficultyLevel.HARD).isEmpty()) {
            selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.HARD, numberOfMissingExercisePoints, hardExercises);
        }

        recommendedOrder.addAll(easyExercises);
        recommendedOrder.addAll(mediumExercises);
        recommendedOrder.addAll(hardExercises);
    }

    /**
     * Selects a given number of exercises of specified difficulty.
     * <p>
     * If there are not sufficiently exercises available, the method returns the number of exercises that could not be selected with the particular difficulty.
     *
     * @param difficultyMap  a map from difficulty level to a set of corresponding exercises
     * @param difficulty     the difficulty level that should be chosen
     * @param exercisePoints the amount of exercise points that should be selected
     * @param exercises      the set to store the selected exercises
     * @return amount of points that are missing, if negative the amount of points that are selected too much
     */
    private static double selectExercisesWithDifficulty(Map<DifficultyLevel, List<Exercise>> difficultyMap, DifficultyLevel difficulty, double exercisePoints,
            List<Exercise> exercises) {
        var remainingExercisePoints = new AtomicDouble(exercisePoints);
        var selectedExercises = difficultyMap.get(difficulty).stream().takeWhile(exercise -> remainingExercisePoints.getAndAdd(-exercise.getMaxPoints()) >= 0)
                .collect(Collectors.toSet());
        exercises.addAll(selectedExercises);
        difficultyMap.get(difficulty).removeAll(selectedExercises);
        return remainingExercisePoints.get();
    }

    /**
     * Computes the average confidence of all prior competencies.
     *
     * @param competency the competency for which the average prior confidence should be computed
     * @param state      the current state of the recommendation (containing the mapping for prior competencies)
     * @return the average confidence of all prior competencies
     */
    private static double computeCombinedPriorConfidence(CourseCompetency competency, RecommendationState state) {
        return state.priorCompetencies.get(competency.getId()).stream().map(state.competencyIdMap::get).flatMap(c -> c.getUserProgress().stream())
                .mapToDouble(CompetencyProgress::getConfidence).sorted().average().orElse(1);
    }

    /**
     * Predicts the additionally needed exercise points required to master the given competency based on prior performance and current progress.
     * <p>
     * The following formulas are used predict the number of exercises required to master the competency:
     * <ul>
     * <li>Mastery >= MasteryThreshold</li>
     * <li>Mastery = Progress * Confidence {@link CompetencyProgressService#getMastery}</li>
     * <li>Progress = (#Exercises / # LearningObjects) * (AchievedPoints / TotalPoints) + #LectureUnits / #LearningObjects {@link CompetencyProgressService#calculateProgress}</li>
     * <li>Confidence ≈ 0.9 * weightedConfidence</li>
     * <li>RequiredPoints = AchievedPoints - CurrentScore</li>
     * </ul>
     * The formulas are substituted and solved for RequiredScore.
     *
     * @param user               the user for which the prediction should be computed
     * @param competency         the competency for which the prediction should be computed
     * @param weightedConfidence the weighted confidence of the current and prior competencies
     * @return the predicted number of exercise points required to master the given competency
     */
    private double calculateNumberOfExercisePointsRequiredToMaster(User user, CourseCompetency competency, double weightedConfidence) {
        // we assume that the student may perform slightly worse than previously and dampen the confidence for the prediction process
        weightedConfidence *= 0.9;
        Set<Exercise> exercises = competency.getExerciseLinks().stream().map(CompetencyExerciseLink::getExercise).collect(Collectors.toSet());
        double currentPoints = participantScoreService.getStudentAndTeamParticipationPointsAsDoubleStream(user, exercises).sum();
        double maxPoints = exercises.stream().mapToDouble(Exercise::getMaxPoints).sum();
        double lectureUnits = competency.getLectureUnitLinks().size();
        double learningObjects = lectureUnits + exercises.size();
        double masteryThreshold = competency.getMasteryThreshold();

        double neededProgress = masteryThreshold / weightedConfidence;
        double maxLectureUnitProgress = lectureUnits / learningObjects * 100;
        double exerciseWeight = exercises.size() / learningObjects;
        double neededTotalExercisePoints = (neededProgress - maxLectureUnitProgress) / exerciseWeight * (maxPoints / 100);

        double neededExercisePoints = neededTotalExercisePoints - currentPoints;
        // numerical edge case, can't happen for valid competencies
        return Math.max(neededExercisePoints, 0);
    }

    /**
     * Generates a map from difficulty level to a set of corresponding exercises.
     *
     * @param exercises the exercises that should be contained in the map
     * @return a map from difficulty level to a set of corresponding exercises
     */
    private static Map<DifficultyLevel, List<Exercise>> generateDifficultyLevelMap(List<Exercise> exercises) {
        Map<DifficultyLevel, List<Exercise>> difficultyLevelMap = new HashMap<>();
        for (var difficulty : DifficultyLevel.values()) {
            difficultyLevelMap.put(difficulty, new ArrayList<>());
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
     * @param numberOfExercisePointsRequiredToMaster the minimum amount of exercise points that should be recommended
     * @param weightedConfidence                     the weighted confidence of the current and prior competencies
     * @return array containing the recommended number of exercises per difficulty level (easy to hard)
     */
    private static double[] getRecommendedExercisePointDistribution(double numberOfExercisePointsRequiredToMaster, double weightedConfidence) {
        final var distribution = getExerciseDifficultyDistribution(weightedConfidence);
        final var numberOfExercisePoints = new double[DifficultyLevel.values().length];
        for (int i = 0; i < numberOfExercisePoints.length; i++) {
            numberOfExercisePoints[i] = distribution[i] * numberOfExercisePointsRequiredToMaster;
        }
        return numberOfExercisePoints;
    }

    /**
     * Retrieves the corresponding distribution from the lookup table.
     *
     * @param weightedConfidence the weighted confidence of the current and prior competencies
     * @return array containing the distribution in percent per difficulty level (easy to hard)
     */
    private static double[] getExerciseDifficultyDistribution(double weightedConfidence) {
        int distributionIndex = (int) Math.round(weightedConfidence * (EXERCISE_DIFFICULTY_DISTRIBUTION_LUT.length - 1));
        return EXERCISE_DIFFICULTY_DISTRIBUTION_LUT[Math.clamp(distributionIndex, 0, EXERCISE_DIFFICULTY_DISTRIBUTION_LUT.length - 1)];
    }

    public record RecommendationState(Map<Long, CourseCompetency> competencyIdMap, List<Long> recommendedOrderOfCompetencies, Set<Long> masteredCompetencies,
            Map<Long, Double> competencyMastery, Map<Long, Set<Long>> matchingClusters, Map<Long, Set<Long>> priorCompetencies, Map<Long, Long> extendsCompetencies,
            Map<Long, Long> assumesCompetencies) {
    }

    /**
     * Gets the recommended order of learning objects for a competency. The finished lecture units and exercises are at the beginning of the list.
     * After that all pending lecture units and exercises needed to master the competency are added.
     *
     * @param competencyId the id of the competency
     * @param user         the user for which the recommendation should be generated
     * @return the recommended order of learning objects
     */
    public List<LearningObject> getOrderOfLearningObjectsForCompetency(long competencyId, User user) {
        CourseCompetency competency = courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsElseThrow(competencyId);
        return getOrderOfLearningObjectsForCompetency(competency, user);
    }

    /**
     * Gets the recommended order of learning objects for a competency. The finished lecture units and exercises are at the beginning of the list.
     * After that all pending lecture units and exercises needed to master the competency are added.
     *
     * @param competency the competency for which the recommendation should be generated
     * @param user       the user for which the recommendation should be generated
     * @return the recommended order of learning objects
     */
    public List<LearningObject> getOrderOfLearningObjectsForCompetency(CourseCompetency competency, User user) {
        Optional<CompetencyProgress> optionalCompetencyProgress = competencyProgressRepository.findByCompetencyIdAndUserId(competency.getId(), user.getId());
        competency.setUserProgress(optionalCompetencyProgress.map(Set::of).orElse(Set.of()));
        Set<LectureUnit> lectureUnits = competency.getLectureUnitLinks().stream().map(CompetencyLectureUnitLink::getLectureUnit).collect(Collectors.toSet());
        learningObjectService.setLectureUnitCompletions(lectureUnits, user);

        Set<CompetencyProgress> priorCompetencyProgresses = competencyProgressRepository.findAllPriorByCompetencyId(competency, user);
        double combinedPriorConfidence = priorCompetencyProgresses.stream().mapToDouble(CompetencyProgress::getConfidence).average().orElse(0);
        double weightedConfidence = computeWeightedConfidence(combinedPriorConfidence, optionalCompetencyProgress);
        Stream<LectureUnit> completedLectureUnits = lectureUnits.stream().filter(lectureUnit -> lectureUnit.isCompletedFor(user));
        Stream<Exercise> completedExercises = competency.getExerciseLinks().stream().map(CompetencyExerciseLink::getExercise)
                .filter(exercise -> learningObjectService.isCompletedByUser(exercise, user));
        Stream<LearningObject> pendingLearningObjects = getRecommendedOrderOfLearningObjects(user, competency, weightedConfidence).stream();

        return Stream.concat(completedLectureUnits, Stream.concat(completedExercises, pendingLearningObjects)).toList();
    }

    /**
     * Computes the weighted confidence of a competency based on the progress of the user and the confidence of the prior competencies.
     * With a higher progress in the current competency, the confidence of the prior competencies is weighted less.
     *
     * @param combinedPriorConfidence    the average confidence of all prior competencies
     * @param optionalCompetencyProgress the progress of the user within the competency
     * @return the weighted confidence of the competency
     */
    private double computeWeightedConfidence(double combinedPriorConfidence, Optional<CompetencyProgress> optionalCompetencyProgress) {
        if (optionalCompetencyProgress.isPresent()) {
            final var competencyProgress = optionalCompetencyProgress.get();
            return (competencyProgress.getProgress() * competencyProgress.getConfidence()) + (1 - competencyProgress.getProgress()) * combinedPriorConfidence;
        }
        else {
            return combinedPriorConfidence;
        }
    }
}
