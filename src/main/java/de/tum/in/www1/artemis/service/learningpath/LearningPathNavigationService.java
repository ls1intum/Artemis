package de.tum.in.www1.artemis.service.learningpath;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.LearningObject;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.service.LearningObjectService;
import de.tum.in.www1.artemis.service.learningpath.LearningPathRecommendationService.RecommendationState;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathNavigationDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathNavigationObjectDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathNavigationObjectDTO.LearningObjectType;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathNavigationOverviewDTO;

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
        var recommendationStateWithAllCompetencies = learningPathRecommendationService.getRecommendedOrderOfAllCompetencies(learningPath);

        // If all competencies are mastered, get the last completed learning object
        if (currentLearningObject == null) {
            currentLearningObject = learningPathRecommendationService.getLastLearningObject(learningPath.getUser(), recommendationStateWithAllCompetencies);
        }
        // If we still didn't find any learning object, there exists no learning object in the learning path and we can return an empty navigation
        if (currentLearningObject == null) {
            return new LearningPathNavigationDTO(null, null, null, learningPath.getProgress());
        }

        long competencyId = recommendationState.recommendedOrderOfCompetencies().getFirst();
        return getNavigationRelativeToLearningObject(recommendationStateWithAllCompetencies, currentLearningObject, competencyId, learningPath);
    }

    /**
     * Get the navigation for the given learning path relative to a given learning object.
     *
     * @param learningPath       the learning path
     * @param learningObjectId   the id of the relative learning object
     * @param learningObjectType the type of the relative learning object
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

    private LearningPathNavigationObjectDTO getPredecessorOfLearningObject(RecommendationState recommendationState, Competency currentCompetency,
            List<LearningObject> learningObjectsInCurrentCompetency, int indexOfCurrentLearningObject, User user) {
        LearningObject predecessorLearningObject = null;
        Competency competencyOfPredecessor = null;
        if (indexOfCurrentLearningObject == 0) {
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

    private LearningPathNavigationObjectDTO getSuccessorOfLearningObject(RecommendationState recommendationState, Competency currentCompetency,
            List<LearningObject> learningObjectsInCurrentCompetency, int indexOfCurrentLearningObject, User user) {
        LearningObject successorLearningObject = null;
        Competency competencyOfSuccessor = null;
        if (indexOfCurrentLearningObject == learningObjectsInCurrentCompetency.size() - 1) {
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

    private LearningPathNavigationObjectDTO createLearningPathNavigationObjectDTO(LearningObject learningObject, User user, Competency competency) {
        if (learningObject == null) {
            return null;
        }

        return LearningPathNavigationObjectDTO.of(learningObject, learningObjectService.isCompletedByUser(learningObject, user), competency.getId());
    }
}
