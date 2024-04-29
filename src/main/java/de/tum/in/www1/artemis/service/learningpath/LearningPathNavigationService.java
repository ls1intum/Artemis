package de.tum.in.www1.artemis.service.learningpath;

import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.LearningObject;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.service.LearningObjectService;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathNavigationDto;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathNavigationDto.LearningPathNavigationObjectDto;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathNavigationDto.LearningPathNavigationObjectDto.LearningObjectType;

@Service
public class LearningPathNavigationService {

    private final LearningPathRecommendationService learningPathRecommendationService;

    private final LearningObjectService learningObjectService;

    public LearningPathNavigationService(LearningPathRecommendationService learningPathRecommendationService, LearningObjectService learningObjectService) {
        this.learningPathRecommendationService = learningPathRecommendationService;
        this.learningObjectService = learningObjectService;
    }

    private LearningPathNavigationDto mapLearningPathObjectNavigationDto(User learningPathUser, int progress, LearningObject predecessorLearningObject,
            LearningObject currentLearningObject, LearningObject successorLearningObject) {
        return new LearningPathNavigationDto(LearningPathNavigationObjectDto.of(predecessorLearningObject, learningPathUser),
                LearningPathNavigationObjectDto.of(currentLearningObject, learningPathUser), LearningPathNavigationObjectDto.of(successorLearningObject, learningPathUser),
                progress);
    }

    public LearningPathNavigationDto getLearningPathNavigation(LearningPath learningPath) {
        var recommendationState = learningPathRecommendationService.getRecommendedOrderOfCompetencies(learningPath);

        var currentLearningObject = learningPathRecommendationService.getCurrentUncompletedLearningObject(learningPath, recommendationState);

        var masteredCompetencies = learningPathRecommendationService.getMasteredCompetencies(learningPath.getCompetencies(), recommendationState.recommendedOrderOfCompetencies());
        var predecessorLearningObject = learningObjectService.getCompletedPredecessorOfLearningObjectRelatedToDate(learningPath.getUser(), Optional.empty(), masteredCompetencies)
                .orElse(null);
        var successorLearningObject = learningPathRecommendationService.getUncompletedSuccessorOfLearningObject(learningPath, recommendationState, currentLearningObject);
        return mapLearningPathObjectNavigationDto(learningPath.getUser(), learningPath.getProgress(), predecessorLearningObject, currentLearningObject, successorLearningObject);
    }

    public LearningPathNavigationDto getLearningPathNavigationRelativeToLearningObject(LearningPath learningPath, Long learningObjectId, LearningObjectType learningObjectType) {
        var currentLearningObject = learningObjectService.getLearningObjectByIdAndType(learningObjectId, learningObjectType);

        var recommendationState = learningPathRecommendationService.getRecommendedOrderOfCompetencies(learningPath);
        var masteredCompetencies = learningPathRecommendationService.getMasteredCompetencies(learningPath.getCompetencies(), recommendationState.recommendedOrderOfCompetencies());

        var learningPathUser = learningPath.getUser();
        var completionDateOptional = currentLearningObject.getCompletionDate(learningPathUser);
        if (completionDateOptional.isPresent()) {
            var predecessorLearningObject = learningObjectService
                    .getCompletedPredecessorOfLearningObjectRelatedToDate(learningPathUser, completionDateOptional, masteredCompetencies).orElse(null);

            var successorLearningObject = learningObjectService.getCompletedSuccessorOfLearningObjectRelatedToDate(learningPathUser, completionDateOptional, masteredCompetencies)
                    .orElseGet(() -> learningPathRecommendationService.getCurrentUncompletedLearningObject(learningPath, recommendationState));
            return mapLearningPathObjectNavigationDto(learningPath.getUser(), learningPath.getProgress(), predecessorLearningObject, currentLearningObject,
                    successorLearningObject);
        }
        var predecessorLearningObject = learningObjectService.getCompletedPredecessorOfLearningObjectRelatedToDate(learningPathUser, completionDateOptional, masteredCompetencies)
                .orElse(learningPathRecommendationService.getUncompletedPredecessorOfLearningObject(currentLearningObject, learningPath, recommendationState));

        var successorLearningObject = learningPathRecommendationService.getUncompletedSuccessorOfLearningObject(learningPath, recommendationState, currentLearningObject);
        return mapLearningPathObjectNavigationDto(learningPath.getUser(), learningPath.getProgress(), predecessorLearningObject, currentLearningObject, successorLearningObject);
    }
}
