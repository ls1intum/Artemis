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
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathNavigationDto;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathNavigationDto.LearningPathNavigationObjectDto;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathNavigationDto.LearningPathNavigationObjectDto.LearningObjectType;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathNavigationOverviewDto;

@Profile(PROFILE_CORE)
@Service
public class LearningPathNavigationService {

    private final LearningPathRecommendationService learningPathRecommendationService;

    private final LearningObjectService learningObjectService;

    public LearningPathNavigationService(LearningPathRecommendationService learningPathRecommendationService, LearningObjectService learningObjectService) {
        this.learningPathRecommendationService = learningPathRecommendationService;
        this.learningObjectService = learningObjectService;
    }

    private LearningPathNavigationDto mapLearningObjectsToNavigationDto(User learningPathUser, int progress, LearningObject predecessorLearningObject,
            LearningObject currentLearningObject, LearningObject successorLearningObject) {
        return new LearningPathNavigationDto(LearningPathNavigationObjectDto.of(predecessorLearningObject, learningPathUser),
                LearningPathNavigationObjectDto.of(currentLearningObject, learningPathUser), LearningPathNavigationObjectDto.of(successorLearningObject, learningPathUser),
                progress);
    }

    private LearningPathNavigationObjectDto mapLearningObjectToNavigationObjectDto(LearningObject learningObject, User learningPathUser) {
        return LearningPathNavigationObjectDto.of(learningObject, learningPathUser);
    }

    public LearningPathNavigationDto getNavigation(LearningPath learningPath) {
        var recommendationState = learningPathRecommendationService.getRecommendedOrderOfCompetencies(learningPath);

        var currentLearningObject = learningPathRecommendationService.getCurrentUncompletedLearningObject(learningPath, recommendationState);

        var predecessorLearningObject = learningObjectService
                .getCompletedPredecessorOfLearningObjectRelatedToDate(learningPath.getUser(), Optional.empty(), learningPath.getCompetencies()).orElse(null);
        var successorLearningObject = learningPathRecommendationService.getUncompletedSuccessorOfLearningObject(learningPath, recommendationState, currentLearningObject);
        return mapLearningObjectsToNavigationDto(learningPath.getUser(), learningPath.getProgress(), predecessorLearningObject, currentLearningObject, successorLearningObject);
    }

    public LearningPathNavigationDto getNavigationRelativeToLearningObject(LearningPath learningPath, Long learningObjectId, LearningObjectType learningObjectType) {
        var currentLearningObject = learningObjectService.getLearningObjectByIdAndType(learningObjectId, learningObjectType);
        var recommendationState = learningPathRecommendationService.getRecommendedOrderOfCompetencies(learningPath);
        if (currentLearningObject.isCompletedFor(learningPath.getUser())) {
            return getNavigationRelativeToCompletedLearningObject(learningPath, currentLearningObject, recommendationState);
        }
        return getNavigationRelativeToUncompletedLearningObject(learningPath, currentLearningObject, recommendationState);
    }

    private LearningPathNavigationDto getNavigationRelativeToCompletedLearningObject(LearningPath learningPath, LearningObject currentLearningObject,
            RecommendationState recommendationState) {
        var learningPathUser = learningPath.getUser();
        var completionDateOptional = currentLearningObject.getCompletionDate(learningPathUser);
        var competencies = learningPath.getCompetencies();

        var predecessorLearningObject = learningObjectService.getCompletedPredecessorOfLearningObjectRelatedToDate(learningPathUser, completionDateOptional, competencies)
                .orElse(null);
        var successorLearningObject = learningObjectService.getCompletedSuccessorOfLearningObjectRelatedToDate(learningPathUser, completionDateOptional, competencies)
                .orElseGet(() -> learningPathRecommendationService.getCurrentUncompletedLearningObject(learningPath, recommendationState));

        return mapLearningObjectsToNavigationDto(learningPathUser, learningPath.getProgress(), predecessorLearningObject, currentLearningObject, successorLearningObject);
    }

    private LearningPathNavigationDto getNavigationRelativeToUncompletedLearningObject(LearningPath learningPath, LearningObject currentLearningObject,
            RecommendationState recommendationState) {
        var predecessorLearningObject = learningPathRecommendationService.getUncompletedPredecessorOfLearningObject(currentLearningObject, learningPath, recommendationState)
                .orElse(learningObjectService.getCompletedPredecessorOfLearningObjectRelatedToDate(learningPath.getUser(), Optional.empty(), learningPath.getCompetencies())
                        .orElse(null));
        var successorLearningObject = learningPathRecommendationService.getUncompletedSuccessorOfLearningObject(learningPath, recommendationState, currentLearningObject);

        return mapLearningObjectsToNavigationDto(learningPath.getUser(), learningPath.getProgress(), predecessorLearningObject, currentLearningObject, successorLearningObject);
    }

    public LearningPathNavigationOverviewDto getNavigationOverview(LearningPath learningPath) {
        var learningPathUser = learningPath.getUser();
        var learningObjects = Stream
                .concat(learningObjectService.getCompletedLearningObjectsForUserAndCompetencies(learningPath.getUser(), learningPath.getCompetencies()),
                        learningPathRecommendationService.getUncompletedLearningObjects(learningPath))
                .map(learningObject -> this.mapLearningObjectToNavigationObjectDto(learningObject, learningPathUser)).distinct().toList();
        return new LearningPathNavigationOverviewDto(learningObjects);
    }
}
