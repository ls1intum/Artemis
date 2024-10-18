package de.tum.cit.aet.artemis.atlas.service.learningpath;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationObjectDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationObjectDTO.LearningObjectType;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationOverviewDTO;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathRecommendationService.RecommendationState;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.lecture.service.LearningObjectService;

/**
 * Service for navigating through a learning path.
 */
@Profile(PROFILE_CORE)
@Service
public class LearningPathNavigationService {

    private final LearningPathRecommendationService learningPathRecommendationService;

    private final LearningObjectService learningObjectService;

    public LearningPathNavigationService(LearningPathRecommendationService learningPathRecommendationService, LearningObjectService learningObjectService) {
        this.learningPathRecommendationService = learningPathRecommendationService;
        this.learningObjectService = learningObjectService;
    }

    /**
     * Get the navigation for the given learning path. The current learning object is the next uncompleted learning object in the learning path or the last completed learning
     * object if all are completed.
     *
     * @param learningPath the learning path
     * @return the navigation
     */
    public LearningPathNavigationDTO getNavigation(LearningPath learningPath) {
        var recommendationState = learningPathRecommendationService.getRecommendedOrderOfNotMasteredCompetencies(learningPath);
        var currentLearningObject = learningPathRecommendationService.getFirstLearningObject(learningPath.getUser(), recommendationState);
        CourseCompetency competencyOfCurrentLearningObject;
        var recommendationStateWithAllCompetencies = learningPathRecommendationService.getRecommendedOrderOfAllCompetencies(learningPath);

        // If all competencies are mastered, get the last completed learning object
        if (currentLearningObject == null) {
            currentLearningObject = learningPathRecommendationService.getLastLearningObject(learningPath.getUser(), recommendationStateWithAllCompetencies);

            if (currentLearningObject == null) {
                // If we still didn't find any learning object, there exists no learning object in the learning path and we can return an empty navigation
                return new LearningPathNavigationDTO(null, null, null, learningPath.getProgress());
            }
            else {
                competencyOfCurrentLearningObject = findCorrespondingCompetencyForLearningObject(recommendationStateWithAllCompetencies, currentLearningObject, false);
                return new LearningPathNavigationDTO(LearningPathNavigationObjectDTO.of(currentLearningObject, true, competencyOfCurrentLearningObject.getId()), null, null,
                        learningPath.getProgress());
            }
        }
        else {
            competencyOfCurrentLearningObject = findCorrespondingCompetencyForLearningObject(recommendationState, currentLearningObject, true);
        }

        return getNavigationRelativeToLearningObject(recommendationStateWithAllCompetencies, currentLearningObject, competencyOfCurrentLearningObject.getId(), learningPath);
    }

    /**
     * Find the correct competency that contains the given learning object.
     * Either the first or last competency that contains the learning object is returned depending on the firstCompetency parameter.
     *
     * @param recommendationState the recommendation state
     * @param learningObject      the learning object
     * @param firstCompetency     whether to find the first or last competency that contains the learning object
     * @return the competency that contains the learning object
     */
    private CourseCompetency findCorrespondingCompetencyForLearningObject(RecommendationState recommendationState, LearningObject learningObject, boolean firstCompetency) {
        Stream<CourseCompetency> potentialCompetencies = recommendationState.recommendedOrderOfCompetencies().stream()
                .map(competencyId -> recommendationState.competencyIdMap().get(competencyId))
                .filter(competency -> competency.getLectureUnitLinks().contains(learningObject) || competency.getExerciseLinks().contains(learningObject));

        // There will always be at least one competency that contains the learning object, otherwise the learning object would not be in the learning path
        Comparator<CourseCompetency> comparator = Comparator.comparingInt(competency -> recommendationState.recommendedOrderOfCompetencies().indexOf(competency.getId()));
        if (firstCompetency) {
            return potentialCompetencies.min(comparator).get();
        }
        else {
            return potentialCompetencies.max(comparator).get();
        }
    }

    /**
     * Get the navigation for the given learning path relative to a given learning object.
     *
     * @param learningPath       the learning path
     * @param learningObjectId   the id of the relative learning object
     * @param learningObjectType the type of the relative learning object
     * @param competencyId       the id of the competency of the relative learning object
     * @return the navigation
     */
    public LearningPathNavigationDTO getNavigationRelativeToLearningObject(LearningPath learningPath, long learningObjectId, LearningObjectType learningObjectType,
            long competencyId) {
        var recommendationState = learningPathRecommendationService.getRecommendedOrderOfAllCompetencies(learningPath);
        var currentLearningObject = learningObjectService.getLearningObjectByIdAndType(learningObjectId, learningObjectType);

        return getNavigationRelativeToLearningObject(recommendationState, currentLearningObject, competencyId, learningPath);
    }

    private LearningPathNavigationDTO getNavigationRelativeToLearningObject(RecommendationState recommendationState, LearningObject currentLearningObject, long competencyId,
            LearningPath learningPath) {
        var currentCompetency = recommendationState.competencyIdMap().get(competencyId);

        var learningObjectsInCurrentCompetency = learningPathRecommendationService.getOrderOfLearningObjectsForCompetency(currentCompetency, learningPath.getUser());
        int indexOfCurrentLearningObject = learningObjectsInCurrentCompetency.indexOf(currentLearningObject);

        var predecessorLearningObjectDTO = getPredecessorOfLearningObject(recommendationState, currentCompetency, learningObjectsInCurrentCompetency, indexOfCurrentLearningObject,
                learningPath.getUser());
        var currentLearningObjectDTO = createLearningPathNavigationObjectDTO(currentLearningObject, learningPath.getUser(), currentCompetency);
        var successorLearningObjectDTO = getSuccessorOfLearningObject(recommendationState, currentCompetency, learningObjectsInCurrentCompetency, indexOfCurrentLearningObject,
                learningPath.getUser());

        return new LearningPathNavigationDTO(predecessorLearningObjectDTO, currentLearningObjectDTO, successorLearningObjectDTO, learningPath.getProgress());
    }

    private LearningPathNavigationObjectDTO getPredecessorOfLearningObject(RecommendationState recommendationState, CourseCompetency currentCompetency,
            List<LearningObject> learningObjectsInCurrentCompetency, int indexOfCurrentLearningObject, User user) {
        LearningObject predecessorLearningObject = null;
        CourseCompetency competencyOfPredecessor = null;
        if (indexOfCurrentLearningObject <= 0) {
            int indexOfCompetencyToSearch = recommendationState.recommendedOrderOfCompetencies().indexOf(currentCompetency.getId()) - 1;
            while (indexOfCompetencyToSearch >= 0 && predecessorLearningObject == null) {
                long competencyIdToSearchNext = recommendationState.recommendedOrderOfCompetencies().get(indexOfCompetencyToSearch);
                var competencyToSearch = recommendationState.competencyIdMap().get(competencyIdToSearchNext);
                var learningObjectsInPreviousCompetency = learningPathRecommendationService.getOrderOfLearningObjectsForCompetency(competencyToSearch, user);
                if (!learningObjectsInPreviousCompetency.isEmpty()) {
                    predecessorLearningObject = learningObjectsInPreviousCompetency.getLast();
                    competencyOfPredecessor = competencyToSearch;
                }
                indexOfCompetencyToSearch--;
            }
        }
        else {
            predecessorLearningObject = learningObjectsInCurrentCompetency.get(indexOfCurrentLearningObject - 1);
            competencyOfPredecessor = currentCompetency;
        }
        return createLearningPathNavigationObjectDTO(predecessorLearningObject, user, competencyOfPredecessor);
    }

    private LearningPathNavigationObjectDTO getSuccessorOfLearningObject(RecommendationState recommendationState, CourseCompetency currentCompetency,
            List<LearningObject> learningObjectsInCurrentCompetency, int indexOfCurrentLearningObject, User user) {
        LearningObject successorLearningObject = null;
        CourseCompetency competencyOfSuccessor = null;
        if (indexOfCurrentLearningObject >= learningObjectsInCurrentCompetency.size() - 1) {
            int indexOfCompetencyToSearch = recommendationState.recommendedOrderOfCompetencies().indexOf(currentCompetency.getId()) + 1;
            while (indexOfCompetencyToSearch < recommendationState.recommendedOrderOfCompetencies().size() && successorLearningObject == null) {
                long competencyIdToSearchNext = recommendationState.recommendedOrderOfCompetencies().get(indexOfCompetencyToSearch);
                var nextCompetencyToSearch = recommendationState.competencyIdMap().get(competencyIdToSearchNext);
                var learningObjectsInNextCompetency = learningPathRecommendationService.getOrderOfLearningObjectsForCompetency(nextCompetencyToSearch, user);
                if (!learningObjectsInNextCompetency.isEmpty()) {
                    successorLearningObject = learningObjectsInNextCompetency.getFirst();
                    competencyOfSuccessor = nextCompetencyToSearch;
                }
                indexOfCompetencyToSearch++;
            }
        }
        else {
            successorLearningObject = learningObjectsInCurrentCompetency.get(indexOfCurrentLearningObject + 1);
            competencyOfSuccessor = currentCompetency;
        }
        return createLearningPathNavigationObjectDTO(successorLearningObject, user, competencyOfSuccessor);
    }

    /**
     * Get the navigation overview for the given learning path.
     *
     * @param learningPath the learning path
     * @return the navigation overview
     */
    public LearningPathNavigationOverviewDTO getNavigationOverview(LearningPath learningPath) {
        var learningPathUser = learningPath.getUser();
        RecommendationState recommendationState = learningPathRecommendationService.getRecommendedOrderOfAllCompetencies(learningPath);
        var learningObjects = recommendationState.recommendedOrderOfCompetencies().stream().map(competencyId -> recommendationState.competencyIdMap().get(competencyId))
                .flatMap(competency -> learningPathRecommendationService.getOrderOfLearningObjectsForCompetency(competency, learningPathUser).stream()
                        .map(learningObject -> createLearningPathNavigationObjectDTO(learningObject, learningPathUser, competency)))
                .toList();
        return new LearningPathNavigationOverviewDTO(learningObjects);
    }

    private LearningPathNavigationObjectDTO createLearningPathNavigationObjectDTO(LearningObject learningObject, User user, CourseCompetency competency) {
        if (learningObject == null) {
            return null;
        }

        return LearningPathNavigationObjectDTO.of(learningObject, learningObjectService.isCompletedByUser(learningObject, user), competency.getId());
    }
}
