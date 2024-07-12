package de.tum.in.www1.artemis.service.learningpath;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.stream.Stream;

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
     * Get the navigation for the given learning path.
     *
     * @param learningPath the learning path
     * @return the navigation
     */
    public LearningPathNavigationDTO getNavigation(LearningPath learningPath) {
        var recommendationState = learningPathRecommendationService.getRecommendedOrderOfNotMasteredCompetencies(learningPath);
        var currentLearningObject = learningPathRecommendationService.getFirstLearningObject(learningPath.getUser(), recommendationState);
        var recommendationStateWithAllCompetencies = learningPathRecommendationService.getRecommendedOrderOfAllCompetencies(learningPath);

        if (currentLearningObject == null) {
            currentLearningObject = learningPathRecommendationService.getLastLearningObject(learningPath.getUser(), recommendationStateWithAllCompetencies);
        }

        return getNavigationRelativeToLearningObject(recommendationStateWithAllCompetencies, currentLearningObject, learningPath);
    }

    /**
     * Get the navigation for the given learning path relative to a given learning object.
     *
     * @param learningPath       the learning path
     * @param learningObjectId   the id of the relative learning object
     * @param learningObjectType the type of the relative learning object
     * @return the navigation
     */
    public LearningPathNavigationDTO getNavigationRelativeToLearningObject(LearningPath learningPath, Long learningObjectId, LearningObjectType learningObjectType) {
        var recommendationState = learningPathRecommendationService.getRecommendedOrderOfAllCompetencies(learningPath);
        var currentLearningObject = learningObjectService.getLearningObjectByIdAndType(learningObjectId, learningObjectType);

        return getNavigationRelativeToLearningObject(recommendationState, currentLearningObject, learningPath);
    }

    private LearningPathNavigationDTO getNavigationRelativeToLearningObject(RecommendationState recommendationState, LearningObject currentLearningObject,
            LearningPath learningPath) {
        if (currentLearningObject == null) {
            return new LearningPathNavigationDTO(null, null, null, learningPath.getProgress());
        }

        var currentCompetency = learningPathRecommendationService.getCompetencyOfLearningObjectOnLearningPath(learningPath.getUser(), currentLearningObject, recommendationState);

        var learningObjectsInCurrentCompetency = learningPathRecommendationService.getOrderOfLearningObjectsForCompetency(currentCompetency, learningPath.getUser());
        int indexOfCurrentLearningObject = learningObjectsInCurrentCompetency.indexOf(currentLearningObject);

        var predecessorLearningObject = getPredecessorOfLearningObject(recommendationState, currentCompetency, learningObjectsInCurrentCompetency, indexOfCurrentLearningObject,
                learningPath.getUser());
        LearningPathNavigationObjectDTO predecessorLearningObjectDTO = createLearningPathNavigationObjectDTO(predecessorLearningObject, learningPath.getUser());

        LearningPathNavigationObjectDTO currentLearningObjectDTO = createLearningPathNavigationObjectDTO(currentLearningObject, learningPath.getUser());

        var successorLearningObject = getSuccessorOfLearningObject(recommendationState, currentCompetency, learningObjectsInCurrentCompetency, indexOfCurrentLearningObject,
                learningPath.getUser());
        LearningPathNavigationObjectDTO successorLearningObjectDTO = createLearningPathNavigationObjectDTO(successorLearningObject, learningPath.getUser());

        return new LearningPathNavigationDTO(predecessorLearningObjectDTO, currentLearningObjectDTO, successorLearningObjectDTO, learningPath.getProgress());
    }

    private LearningObject getPredecessorOfLearningObject(RecommendationState recommendationState, Competency currentCompetency,
            List<LearningObject> learningObjectsInCurrentCompetency, int indexOfCurrentLearningObject, User user) {
        LearningObject predecessorLearningObject = null;
        if (indexOfCurrentLearningObject == 0) {
            int indexOfCompetencyToSearch = recommendationState.recommendedOrderOfCompetencies().indexOf(currentCompetency.getId()) - 1;
            while (indexOfCompetencyToSearch >= 0 && predecessorLearningObject == null) {
                long competencyIdToSearchNext = recommendationState.recommendedOrderOfCompetencies().get(indexOfCompetencyToSearch);
                var competencyToSearch = recommendationState.competencyIdMap().get(competencyIdToSearchNext);
                var learningObjectsInPreviousCompetency = learningPathRecommendationService.getOrderOfLearningObjectsForCompetency(competencyToSearch, user);
                if (!learningObjectsInPreviousCompetency.isEmpty()) {
                    predecessorLearningObject = learningObjectsInPreviousCompetency.getLast();
                }
                indexOfCompetencyToSearch--;
            }
        }
        else {
            predecessorLearningObject = learningObjectsInCurrentCompetency.get(indexOfCurrentLearningObject - 1);
        }
        return predecessorLearningObject;
    }

    private LearningObject getSuccessorOfLearningObject(RecommendationState recommendationState, Competency currentCompetency,
            List<LearningObject> learningObjectsInCurrentCompetency, int indexOfCurrentLearningObject, User user) {
        LearningObject successorLearningObject = null;
        if (indexOfCurrentLearningObject == learningObjectsInCurrentCompetency.size() - 1) {
            int indexOfCompetencyToSearch = recommendationState.recommendedOrderOfCompetencies().indexOf(currentCompetency.getId()) + 1;
            while (indexOfCompetencyToSearch < recommendationState.recommendedOrderOfCompetencies().size() && successorLearningObject == null) {
                long competencyIdToSearchNext = recommendationState.recommendedOrderOfCompetencies().get(indexOfCompetencyToSearch);
                var nextCompetencyToSearch = recommendationState.competencyIdMap().get(competencyIdToSearchNext);
                var learningObjectsInNextCompetency = learningPathRecommendationService.getOrderOfLearningObjectsForCompetency(nextCompetencyToSearch, user);
                if (!learningObjectsInNextCompetency.isEmpty()) {
                    successorLearningObject = learningObjectsInNextCompetency.getFirst();
                }
                indexOfCompetencyToSearch++;
            }
        }
        else {
            successorLearningObject = learningObjectsInCurrentCompetency.get(indexOfCurrentLearningObject + 1);
        }
        return successorLearningObject;
    }

    /**
     * Get the navigation overview for the given learning path.
     *
     * @param learningPath the learning path
     * @return the navigation overview
     */
    public LearningPathNavigationOverviewDTO getNavigationOverview(LearningPath learningPath) {
        var learningPathUser = learningPath.getUser();
        var learningObjects = Stream
                .concat(learningObjectService.getCompletedLearningObjectsForUserAndCompetencies(learningPath.getUser(), learningPath.getCompetencies()),
                        learningPathRecommendationService.getUncompletedLearningObjects(learningPath))
                .map(learningObject -> createLearningPathNavigationObjectDTO(learningObject, learningPathUser)).distinct().toList();
        return new LearningPathNavigationOverviewDTO(learningObjects);
    }

    private LearningPathNavigationObjectDTO createLearningPathNavigationObjectDTO(LearningObject learningObject, User user) {
        if (learningObject == null) {
            return null;
        }

        return LearningPathNavigationObjectDTO.of(learningObject, learningObjectService.isCompletedByUser(learningObject, user));
    }
}
