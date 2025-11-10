package de.tum.cit.aet.artemis.atlas.service.learningpath;

import static de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore.INCLUDED_AS_BONUS;
import static de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore.INCLUDED_COMPLETELY;

import java.time.Instant;
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

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreService;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.domain.profile.PreferenceScale;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationObjectDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.LearningObjectService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.BaseExercise;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * Service Implementation for the recommendation of competencies and learning objects in learning paths.
 */
@Conditional(AtlasEnabled.class)
@Lazy
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

    private static final double COMPETENCY_LINK_WEIGHT_TO_GRADE_AIM_RATIO = 2;

    private static final int MIN_DAYS_BETWEEN_REPETITION = 7;

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
     * Determines the competencies that should be repeated by the user based on the course learner profile.
     *
     * @param recommendationState  the current state of the recommendation system
     * @param courseLearnerProfile the course learner profile of the user
     * @return the competencies that should be repeated
     */
    public List<CourseCompetency> determineCompetenciesForRepeatedTests(RecommendationState recommendationState, CourseLearnerProfile courseLearnerProfile) {
        return recommendationState.competencyIdMap.values().stream().filter(competency -> {
            Optional<CompetencyProgress> progress = competency.getUserProgress().stream().findAny();
            if (progress.isEmpty()) {
                return false;
            }

            int repetitionThresholdDays;
            if (competency.getSoftDueDate() != null) {
                int daysSinceSoftDueDate = (int) ((ZonedDateTime.now().toEpochSecond() - competency.getSoftDueDate().toEpochSecond()) / (24 * 60 * 60));
                repetitionThresholdDays = Math.max(MIN_DAYS_BETWEEN_REPETITION, daysSinceSoftDueDate / (courseLearnerProfile.getTimeInvestment() + 1));
            }
            else {
                repetitionThresholdDays = MIN_DAYS_BETWEEN_REPETITION;
            }
            Instant repetitionThreshold = Instant.now().minus(repetitionThresholdDays, ChronoUnit.DAYS);

            return progress.get().getLastModifiedDate().isBefore(repetitionThreshold);
        }).sorted(Comparator.comparing(competency -> competency.getUserProgress().stream().findAny().orElseThrow().getLastModifiedDate())).toList();
    }

    /**
     * Finds the next learning object in the learning path. If a current learning object is present, the next learning object must come after it.
     *
     * @param user                         the user that should get the recommendation
     * @param recommendationState          the current state of the recommendation system
     * @param competenciesForRepeatedTests the competencies that should be repeated
     * @param currentLearningObject        the current learning object or null if the first learning object should be recommended
     * @return the recommended learning object
     */
    public LearningPathNavigationObjectDTO findLearningObject(User user, RecommendationState recommendationState, List<CourseCompetency> competenciesForRepeatedTests,
            LearningPathNavigationObjectDTO currentLearningObject) {
        Long firstCompetencyId = recommendationState.recommendedOrderOfCompetencies().getFirst();

        // We only need the completed learning objects if we look for the next learning object after a completed learning object
        Function<CourseCompetency, List<LearningObject>> getLearningObjects;
        if (currentLearningObject == null || !currentLearningObject.completed()) {
            getLearningObjects = competency -> getRecommendedOrderOfLearningObjects(user, competency, recommendationState, false);
        }
        else {
            getLearningObjects = competency -> getOrderOfLearningObjectsForCompetency(competency, user, currentLearningObject.repeatedTest());
        }

        // Finished learning path or finished last competency -> try to find a competency for repetition
        if (firstCompetencyId == null) {
            return findLearningObjectInCompetencies(user, competenciesForRepeatedTests, true, currentLearningObject, getLearningObjects);
        }
        else if (recommendationState.competencyMastery().get(firstCompetencyId) == 0) {
            var learningObject = findLearningObjectInCompetencies(user, competenciesForRepeatedTests, true, currentLearningObject, getLearningObjects);
            if (learningObject != null) {
                return learningObject;
            }
        }

        List<CourseCompetency> competencies = recommendationState.recommendedOrderOfCompetencies().stream().map(recommendationState.competencyIdMap()::get).toList();
        return findLearningObjectInCompetencies(user, competencies, false, currentLearningObject, getLearningObjects);
    }

    /**
     * Finds the next learning object in the provided competencies. If the current learning object is provided, next learning object must come after it.
     *
     * @param user                  the user that should get the recommendation
     * @param competencies          the competencies that should be considered
     * @param repeatedTest          whether the learning object should be repeated
     * @param currentLearningObject the current learning object or null if the first learning object should be recommended
     * @param getLearningObjects    function to get the learning objects of a competency
     * @return the recommended learning object
     */
    private LearningPathNavigationObjectDTO findLearningObjectInCompetencies(User user, List<CourseCompetency> competencies, boolean repeatedTest,
            LearningPathNavigationObjectDTO currentLearningObject, Function<CourseCompetency, List<LearningObject>> getLearningObjects) {
        boolean foundCurrentLearningObject = currentLearningObject == null;
        for (CourseCompetency competency : competencies) {
            List<LearningObject> learningObjects = getLearningObjects.apply(competency);
            for (LearningObject learningObject : learningObjects) {
                if (currentLearningObject != null && currentLearningObject.equalsLearningObject(learningObject, competency)) {
                    foundCurrentLearningObject = true;
                    continue;
                }
                else if (!foundCurrentLearningObject) {
                    continue;
                }

                return LearningPathNavigationObjectDTO.of(learningObject, repeatedTest, learningObjectService.isCompletedByUser(learningObject, user), competency.getId());
            }
        }
        return null;
    }

    /**
     * Finds the previous learning object.
     *
     * @param learningPath          the learning path that should be analyzed
     * @param recommendationState   the current state of the recommendation system
     * @param currentLearningObject the current learning object the navigation should be relative to
     * @return the previously recommended learning object
     */
    public LearningPathNavigationObjectDTO findPreviousLearningObject(LearningPath learningPath, RecommendationState recommendationState,
            LearningPathNavigationObjectDTO currentLearningObject) {
        if (currentLearningObject != null && !currentLearningObject.repeatedTest()) {
            CourseCompetency currentCompetency = recommendationState.competencyIdMap().get(currentLearningObject.competencyId());
            List<LearningObject> learnObjectsOfCurrentCompetency = getOrderOfLearningObjectsForCompetency(currentCompetency, learningPath.getUser(), false);
            LearningPathNavigationObjectDTO previousLearningObject = getPreviousLearningObjectInCompetency(learningPath, currentLearningObject, currentCompetency,
                    learnObjectsOfCurrentCompetency);
            if (previousLearningObject != null) {
                return previousLearningObject;
            }
        }

        RecommendationState recommendationStateWithAllCompetencies = getRecommendedOrderOfAllCompetencies(learningPath);
        int indexToSearch = getCompetencyIndexOfCurrentLearningObject(recommendationState, currentLearningObject, recommendationStateWithAllCompetencies);

        for (int i = indexToSearch; i >= 0; i--) {
            CourseCompetency competency = recommendationStateWithAllCompetencies.competencyIdMap()
                    .get(recommendationStateWithAllCompetencies.recommendedOrderOfCompetencies().get(i));

            boolean repeatedTests = currentLearningObject != null && currentLearningObject.repeatedTest();
            List<LearningObject> learningObjects = getOrderOfLearningObjectsForCompetency(competency, learningPath.getUser(), repeatedTests);
            if (learningObjects.isEmpty()) {
                continue;
            }

            // If we have a current learning object and it is not a repeated test, we need to find the previous learning object in the same competency
            if (i == indexToSearch && currentLearningObject != null && !currentLearningObject.repeatedTest()) {
                LearningPathNavigationObjectDTO previousLearningObject = getPreviousLearningObjectInCompetency(learningPath, currentLearningObject, competency, learningObjects);
                if (previousLearningObject != null) {
                    return previousLearningObject;
                }
            }
            // Otherwise get the last learning object of the competency
            else {
                LearningObject lastLearningObject = learningObjects.getLast();
                return LearningPathNavigationObjectDTO.of(lastLearningObject, false, learningObjectService.isCompletedByUser(lastLearningObject, learningPath.getUser()),
                        competency.getId());
            }
        }
        return null;
    }

    private int getCompetencyIndexOfCurrentLearningObject(RecommendationState recommendationState, LearningPathNavigationObjectDTO currentLearningObject,
            RecommendationState recommendationStateWithAllCompetencies) {
        if (currentLearningObject == null || currentLearningObject.repeatedTest()) {
            Optional<CourseCompetency> firstNonMasteredCompetency = recommendationStateWithAllCompetencies.recommendedOrderOfCompetencies().stream()
                    .map(recommendationState.competencyIdMap::get)
                    .filter(competency -> recommendationState.competencyMastery().get(competency.getId()) >= competency.getMasteryThreshold()).findFirst();
            CourseCompetency competencyToSearch = firstNonMasteredCompetency
                    .orElse(recommendationState.competencyIdMap().get(recommendationState.recommendedOrderOfCompetencies().getLast()));
            return recommendationStateWithAllCompetencies.recommendedOrderOfCompetencies().indexOf(competencyToSearch.getId());
        }
        else {
            return recommendationStateWithAllCompetencies.recommendedOrderOfCompetencies().indexOf(currentLearningObject.competencyId());
        }
    }

    /**
     * Finds the previous learning object.
     *
     * @param learningPath          the learning path that should be analyzed
     * @param currentLearningObject the current learning object the navigation should be relative to
     * @param competency            the competency to look for the previous learning object
     * @param learningObjects       the learning objects of the competency
     * @return the previously recommended learning object
     */
    private LearningPathNavigationObjectDTO getPreviousLearningObjectInCompetency(LearningPath learningPath, LearningPathNavigationObjectDTO currentLearningObject,
            CourseCompetency competency, List<LearningObject> learningObjects) {
        for (int j = 1; j < learningObjects.size(); j++) {
            if (currentLearningObject.equalsLearningObject(learningObjects.get(j), competency)) {
                LearningObject previousLearningObject = learningObjects.get(j - 1);
                return LearningPathNavigationObjectDTO.of(previousLearningObject, false, learningObjectService.isCompletedByUser(previousLearningObject, learningPath.getUser()),
                        competency.getId());
            }
        }
        return null;
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
     * @param user          the user that should be analyzed
     * @param competency    the competency
     * @param state         the current state of the recommendation
     * @param repeatedTests whether the learning object is meant as a repeated test
     * @return the recommended ordering of learning objects
     */
    public List<LearningObject> getRecommendedOrderOfLearningObjects(User user, CourseCompetency competency, RecommendationState state, boolean repeatedTests) {
        final var combinedPriorConfidence = computeCombinedPriorConfidence(competency, state);
        return getRecommendedOrderOfLearningObjects(user, competency, combinedPriorConfidence, repeatedTests);
    }

    /**
     * Analyzes the current progress within the learning path and generates a recommended ordering of uncompleted learning objects in a competency.
     * The ordering is based on the competency link weights in decreasing order
     *
     * @param user                    the user that should be analyzed
     * @param competency              the competency
     * @param combinedPriorConfidence the combined confidence of the user for the prior competencies
     * @param repeatedTests           whether the learning object is meant as a repeated test
     * @return the recommended ordering of learning objects
     */
    public List<LearningObject> getRecommendedOrderOfLearningObjects(User user, CourseCompetency competency, double combinedPriorConfidence, boolean repeatedTests) {
        var learnerProfile = user.getLearnerProfile();
        var courseLearnerProfile = learnerProfile.getCourseLearnerProfiles().stream().findFirst().orElse(new CourseLearnerProfile());

        var pendingLectureUnits = competency.getLectureUnitLinks().stream().sorted(Comparator.comparingDouble(CompetencyLectureUnitLink::getWeight).reversed())
                .map(CompetencyLectureUnitLink::getLectureUnit).filter(lectureUnit -> !lectureUnit.isCompletedFor(user)).toList();
        List<LearningObject> recommendedOrder = new ArrayList<>(pendingLectureUnits);

        // early return if competency can be trivially mastered
        if (CompetencyProgressService.canBeMasteredWithoutExercises(competency)) {
            return recommendedOrder;
        }

        final var optionalCompetencyProgress = competency.getUserProgress().stream().findAny();
        final double weightedConfidence = computeWeightedConfidence(combinedPriorConfidence, optionalCompetencyProgress);

        var numberOfRequiredExercisePointsToMaster = calculateNumberOfExercisePointsRequiredToMaster(user, competency, weightedConfidence);
        if (repeatedTests) {
            numberOfRequiredExercisePointsToMaster *= 1 + courseLearnerProfile.getRepetitionIntensity() / 10.0;
        }

        final var pendingExercises = competency.getExerciseLinks().stream().filter(link -> !learningObjectService.isCompletedByUser(link.getExercise(), user))
                .sorted(getExerciseOrderComparator(courseLearnerProfile.getAimForGradeOrBonus())).map(CompetencyExerciseLink::getExercise).toList();

        final var pendingExercisePoints = pendingExercises.stream().mapToDouble(BaseExercise::getMaxPoints).sum();

        Map<DifficultyLevel, List<Exercise>> difficultyLevelMap = generateDifficultyLevelMap(pendingExercises);
        if (numberOfRequiredExercisePointsToMaster >= pendingExercisePoints) {
            scheduleAllExercises(recommendedOrder, difficultyLevelMap);
            return recommendedOrder;
        }
        final var recommendedExerciseDistribution = getRecommendedExercisePointDistribution(numberOfRequiredExercisePointsToMaster, weightedConfidence);

        distributeExercisesByDistribution(recommendedOrder, recommendedExerciseDistribution, difficultyLevelMap, courseLearnerProfile);
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
    private void distributeExercisesByDistribution(List<LearningObject> recommendedOrder, double[] recommendedExercisePointDistribution,
            Map<DifficultyLevel, List<Exercise>> difficultyMap, CourseLearnerProfile courseLearnerProfile) {
        final var easyExercises = new ArrayList<Exercise>();
        final var mediumExercises = new ArrayList<Exercise>();
        final var hardExercises = new ArrayList<Exercise>();

        // choose as many exercises from the correct difficulty level as possible
        final var missingEasy = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.EASY, recommendedExercisePointDistribution[0], easyExercises, courseLearnerProfile);
        final var missingHard = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.HARD, recommendedExercisePointDistribution[2], hardExercises, courseLearnerProfile);

        // if there are not sufficiently many exercises per difficulty level, prefer medium difficulty
        // case 1: no medium exercises available/medium exercises missing: continue to fill with easy/hard exercises
        // case 2: medium exercises available: no medium exercises missing -> missing exercises must be easy/hard -> in both scenarios medium is the closest difficulty level
        double mediumExercisePoints = recommendedExercisePointDistribution[1] + missingEasy + missingHard;
        double numberOfMissingExercisePoints = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.MEDIUM, mediumExercisePoints, mediumExercises, courseLearnerProfile);

        // if there are still not sufficiently many medium exercises, choose easy difficulty
        // prefer easy to hard exercises to avoid student overload
        if (numberOfMissingExercisePoints > 0 && !difficultyMap.get(DifficultyLevel.EASY).isEmpty()) {
            numberOfMissingExercisePoints = selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.EASY, numberOfMissingExercisePoints, easyExercises, courseLearnerProfile);
        }

        // fill remaining slots with hard difficulty
        if (numberOfMissingExercisePoints > 0 && !difficultyMap.get(DifficultyLevel.HARD).isEmpty()) {
            selectExercisesWithDifficulty(difficultyMap, DifficultyLevel.HARD, numberOfMissingExercisePoints, hardExercises, courseLearnerProfile);
        }

        recommendedOrder.addAll(easyExercises);
        recommendedOrder.addAll(mediumExercises);
        recommendedOrder.addAll(hardExercises);
    }

    /**
     * Selects exercises of a given difficulty until the target points are met.
     * If the target cannot be met, it returns the remaining (positive) points.
     * If we overshoot, it returns a negative remainder.
     * <p>
     * Selection stops at the first exercise that is neither needed to reach the target
     * nor allowed by the learner's preference (HIGH/MEDIUM_HIGH "bonus" rules).
     * <p>
     * Side effects:
     * - Appends selected exercises to {@code selectedExercises}
     * - Removes the same exercises from {@code exercisesByDifficulty[difficulty]}
     */
    private static double selectExercisesWithDifficulty(Map<DifficultyLevel, List<Exercise>> exercisesByDifficulty, DifficultyLevel difficulty,
            double targetPointsForThisDifficulty, List<Exercise> selectedExercises, CourseLearnerProfile learnerProfile) {

        // Always operate on the list for the requested difficulty (may be empty).
        final List<Exercise> availableAtThisDifficulty = exercisesByDifficulty.getOrDefault(difficulty, List.of());

        // Convert stored int to enum (falls back to MEDIUM if an unknown value appears).
        final PreferenceScale preference = toPreferenceScale(learnerProfile.getAimForGradeOrBonus());

        // Running remainder of points we still aim to schedule for this difficulty.
        double remainingPointsToSchedule = targetPointsForThisDifficulty;

        // We accumulate here, and only mutate the source list after selection completes.
        final List<Exercise> picked = new ArrayList<>();

        for (Exercise exercise : availableAtThisDifficulty) {
            final double pointsBeforeThisExercise = remainingPointsToSchedule;
            remainingPointsToSchedule -= exercise.getMaxPoints();

            final boolean neededToReachTarget = pointsBeforeThisExercise >= 0;
            final boolean allowedByPreference = isEligibleByPreference(preference, exercise);

            if (neededToReachTarget || allowedByPreference) {
                picked.add(exercise);
            }
            else {
                // stop at the first "not needed and not allowed".
                break;
            }
        }

        // Apply side effects in a single step (avoids concurrent modification).
        selectedExercises.addAll(picked);
        // Remove from the *original* modifiable list if present.
        exercisesByDifficulty.getOrDefault(difficulty, new ArrayList<>()).removeAll(picked);

        return remainingPointsToSchedule; // positive = missing, negative = overshoot
    }

    /** Maps an int value from the profile to the enum, defaulting to MEDIUM if unknown. */
    private static PreferenceScale toPreferenceScale(int value) {
        for (PreferenceScale scale : PreferenceScale.values()) {
            if (scale.getValue() == value) {
                return scale;
            }
        }
        return PreferenceScale.MEDIUM;
    }

    /**
     * Preference-based "bonus acceptance" rule:
     * - HIGH: accept COMPLETELY and BONUS exercises even when the target is already exceeded
     * - MEDIUM_HIGH: accept COMPLETELY exercises even when the target is already exceeded
     * - Others: no extra acceptance (only accept while still needed)
     */
    private static boolean isEligibleByPreference(PreferenceScale preference, Exercise exercise) {
        return switch (preference) {
            case HIGH -> exercise.getIncludedInOverallScore() == INCLUDED_COMPLETELY || exercise.getIncludedInOverallScore() == INCLUDED_AS_BONUS;
            case MEDIUM_HIGH -> exercise.getIncludedInOverallScore() == INCLUDED_COMPLETELY;
            default -> false;
        };
    }

    private static int getIncludeInOverallScoreWeight(IncludedInOverallScore includedInOverallScore) {
        return switch (includedInOverallScore) {
            case INCLUDED_COMPLETELY -> 0;
            case INCLUDED_AS_BONUS -> 1;
            case NOT_INCLUDED -> 2;
        };
    }

    /**
     * Creates a comparator that orders exercises based on the aim for grade or bonus, the link weight for the current competency and as a tiebreaker the lexicographic order of
     * the exercise title. The higher the aim for the grade bonus is, the higher this metric is weighted compared to the link weight.
     *
     * @param aimForGradeOrBonus the aim for grade or bonus
     * @return the comparator that orders the exercise based on the preference
     */
    private static Comparator<CompetencyExerciseLink> getExerciseOrderComparator(int aimForGradeOrBonus) {
        Comparator<CompetencyExerciseLink> exerciseComparator = Comparator.comparingDouble(exerciseLink -> (COMPETENCY_LINK_WEIGHT_TO_GRADE_AIM_RATIO * exerciseLink.getWeight())
                + aimForGradeOrBonus * getIncludeInOverallScoreWeight(exerciseLink.getExercise().getIncludedInOverallScore()));
        exerciseComparator = exerciseComparator.reversed();

        exerciseComparator = exerciseComparator.thenComparing(exerciseLink -> exerciseLink.getExercise().getTitle());
        return exerciseComparator;
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
        return getOrderOfLearningObjectsForCompetency(competency, user, false);
    }

    /**
     * Gets the recommended order of learning objects for a competency. The finished lecture units and exercises are at the beginning of the list.
     * After that all pending lecture units and exercises needed to master the competency are added.
     *
     * @param competency    the competency for which the recommendation should be generated
     * @param user          the user for which the recommendation should be generated
     * @param repeatedTests whether the learning object is meant as a repeated test
     * @return the recommended order of learning objects
     */
    public List<LearningObject> getOrderOfLearningObjectsForCompetency(CourseCompetency competency, User user, boolean repeatedTests) {
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
        Stream<LearningObject> pendingLearningObjects = getRecommendedOrderOfLearningObjects(user, competency, weightedConfidence, repeatedTests).stream();

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
