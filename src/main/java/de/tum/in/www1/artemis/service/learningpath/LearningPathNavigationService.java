package de.tum.in.www1.artemis.service.learningpath;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.LearningObject;
import de.tum.in.www1.artemis.domain.User;
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
     * Map learning objects to a navigation DTO.
     *
     * @param learningPathUser          the user of the learning path
     * @param progress                  the progress of the learning path
     * @param predecessorLearningObject the predecessor learning object
     * @param currentLearningObject     the current learning object
     * @param successorLearningObject   the successor learning object
     * @return the navigation DTO
     */
    private LearningPathNavigationDTO mapLearningObjectsToNavigationDto(User learningPathUser, int progress, LearningObject predecessorLearningObject,
            LearningObject currentLearningObject, LearningObject successorLearningObject) {
        return new LearningPathNavigationDTO(LearningPathNavigationObjectDTO.of(predecessorLearningObject, learningPathUser),
                LearningPathNavigationObjectDTO.of(currentLearningObject, learningPathUser), LearningPathNavigationObjectDTO.of(successorLearningObject, learningPathUser),
                progress);
    }

    /**
     * Get the navigation for the given learning path.
     *
     * @param learningPath the learning path
     * @return the navigation
     */
    public LearningPathNavigationDTO getNavigation(LearningPath learningPath) {
        var recommendationState = learningPathRecommendationService.getRecommendedOrderOfNotMasteredCompetencies(learningPath);

        var currentLearningObject = learningPathRecommendationService.getCurrentUncompletedLearningObject(learningPath.getUser(), recommendationState);

        var predecessorLearningObject = learningObjectService
                .getCompletedPredecessorOfLearningObjectRelatedToDate(learningPath.getUser(), Optional.empty(), learningPath.getCompetencies()).orElse(null);
        var successorLearningObject = learningPathRecommendationService.getUncompletedSuccessorOfLearningObject(learningPath.getUser(), recommendationState, currentLearningObject);
        return mapLearningObjectsToNavigationDto(learningPath.getUser(), learningPath.getProgress(), predecessorLearningObject, currentLearningObject, successorLearningObject);
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
        var currentLearningObject = learningObjectService.getLearningObjectByIdAndType(learningObjectId, learningObjectType);
        var recommendationState = learningPathRecommendationService.getRecommendedOrderOfNotMasteredCompetencies(learningPath);
        if (currentLearningObject.isCompletedFor(learningPath.getUser())) {
            return getNavigationRelativeToCompletedLearningObject(learningPath, currentLearningObject, recommendationState);
        }
        return getNavigationRelativeToUncompletedLearningObject(learningPath, currentLearningObject, recommendationState);
    }

    /**
     * Get the navigation for the given learning path relative to a completed learning object.
     *
     * @param learningPath           the learning path
     * @param relativeLearningObject the relative learning object
     * @param recommendationState    the recommendation state of the learning path
     * @return the navigation
     */
    private LearningPathNavigationDTO getNavigationRelativeToCompletedLearningObject(LearningPath learningPath, LearningObject relativeLearningObject,
            RecommendationState recommendationState) {
        var learningPathUser = learningPath.getUser();
        var completionDateOptional = relativeLearningObject.getCompletionDate(learningPathUser);
        var competencies = learningPath.getCompetencies();

        var predecessorLearningObject = learningObjectService.getCompletedPredecessorOfLearningObjectRelatedToDate(learningPathUser, completionDateOptional, competencies)
                .orElse(null);
        var successorLearningObject = learningObjectService.getCompletedSuccessorOfLearningObjectRelatedToDate(learningPathUser, completionDateOptional, competencies)
                .orElseGet(() -> learningPathRecommendationService.getCurrentUncompletedLearningObject(learningPath.getUser(), recommendationState));

        return mapLearningObjectsToNavigationDto(learningPathUser, learningPath.getProgress(), predecessorLearningObject, relativeLearningObject, successorLearningObject);
    }

    /**
     * Get the navigation for the given learning path relative to an uncompleted learning object.
     *
     * @param learningPath           the learning path
     * @param relativeLearningObject the relative learning object
     * @param recommendationState    the recommendation state of the learning path
     * @return the navigation
     */
    private LearningPathNavigationDTO getNavigationRelativeToUncompletedLearningObject(LearningPath learningPath, LearningObject relativeLearningObject,
            RecommendationState recommendationState) {
        var predecessorLearningObject = learningPathRecommendationService
                .getUncompletedPredecessorOfLearningObject(relativeLearningObject, learningPath.getUser(), recommendationState).orElse(learningObjectService
                        .getCompletedPredecessorOfLearningObjectRelatedToDate(learningPath.getUser(), Optional.empty(), learningPath.getCompetencies()).orElse(null));
        var successorLearningObject = learningPathRecommendationService.getUncompletedSuccessorOfLearningObject(learningPath.getUser(), recommendationState,
                relativeLearningObject);

        return mapLearningObjectsToNavigationDto(learningPath.getUser(), learningPath.getProgress(), predecessorLearningObject, relativeLearningObject, successorLearningObject);
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
                .map(learningObject -> LearningPathNavigationObjectDTO.of(learningObject, learningPathUser)).distinct().toList();
        return new LearningPathNavigationOverviewDTO(learningObjects);
    }
}
