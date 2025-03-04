package de.tum.cit.aet.artemis.atlas.service.learningpath;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATLAS;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
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
@Profile(PROFILE_ATLAS)
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
        LearnerProfile learnerProfile = learningPath.getUser().getLearnerProfile();
        CourseLearnerProfile courseLearnerProfile = learnerProfile.getCourseLearnerProfiles().stream().findAny().orElse(new CourseLearnerProfile());
        RecommendationState recommendationState = learningPathRecommendationService.getRecommendedOrderOfNotMasteredCompetencies(learningPath);
        List<CourseCompetency> competenciesForRepeatedTests = learningPathRecommendationService.determineCompetenciesForRepeatedTests(recommendationState, courseLearnerProfile);

        LearningPathNavigationObjectDTO currentLearningObject = learningPathRecommendationService.findLearningObject(learningPath.getUser(), recommendationState,
                competenciesForRepeatedTests, null);
        return getNavigationRelativeToLearningObject(learningPath, recommendationState, competenciesForRepeatedTests, currentLearningObject);
    }

    /**
     * Get the navigation for the given learning path relative to a given learning object.
     *
     * @param learningPath       the learning path
     * @param learningObjectId   the id of the relative learning object
     * @param learningObjectType the type of the relative learning object
     * @param competencyId       the id of the competency of the relative learning object
     * @param repeatedTest       whether the relative learning object is part of a repeated test
     * @return the navigation
     */
    public LearningPathNavigationDTO getNavigationRelativeToLearningObject(LearningPath learningPath, long learningObjectId, LearningObjectType learningObjectType,
            long competencyId, boolean repeatedTest) {
        LearnerProfile learnerProfile = learningPath.getUser().getLearnerProfile();
        CourseLearnerProfile courseLearnerProfile = learnerProfile.getCourseLearnerProfiles().stream().findAny().orElse(new CourseLearnerProfile());
        RecommendationState recommendationState = learningPathRecommendationService.getRecommendedOrderOfNotMasteredCompetencies(learningPath);
        List<CourseCompetency> competenciesForRepeatedTests = learningPathRecommendationService.determineCompetenciesForRepeatedTests(recommendationState, courseLearnerProfile);

        CourseCompetency currentCompetency = recommendationState.competencyIdMap().get(competencyId);
        LearningObject currentLearningObject = switch (learningObjectType) {
            case LECTURE -> currentCompetency.getLectureUnitLinks().stream().map(CompetencyLectureUnitLink::getLectureUnit)
                    .filter(lectureUnit -> lectureUnit.getId() == learningObjectId).findAny().orElse(null);
            case EXERCISE -> currentCompetency.getExerciseLinks().stream().map(CompetencyExerciseLink::getExercise).filter(exercise -> exercise.getId() == learningObjectId)
                    .findAny().orElse(null);
        };

        return getNavigationRelativeToLearningObject(learningPath, recommendationState, competenciesForRepeatedTests,
                createLearningPathNavigationObjectDTO(currentLearningObject, repeatedTest, learningPath.getUser(), currentCompetency));
    }

    /**
     * Get the navigation for the given learning path relative to a given learning object.
     *
     * @param learningPath                 the learning path
     * @param recommendationState          the current state of the recommendation system
     * @param competenciesForRepeatedTests the competencies that should be repeated
     * @param currentLearningObject        the current learning object the navigation should be relative to
     * @return the navigation
     */
    private LearningPathNavigationDTO getNavigationRelativeToLearningObject(LearningPath learningPath, RecommendationState recommendationState,
            List<CourseCompetency> competenciesForRepeatedTests, LearningPathNavigationObjectDTO currentLearningObject) {
        LearningPathNavigationObjectDTO previousLearningObject = learningPathRecommendationService.findPreviousLearningObject(learningPath, recommendationState,
                currentLearningObject);
        LearningPathNavigationObjectDTO nextLearningObject = learningPathRecommendationService.findLearningObject(learningPath.getUser(), recommendationState,
                competenciesForRepeatedTests, currentLearningObject);

        return new LearningPathNavigationDTO(previousLearningObject, currentLearningObject, nextLearningObject, learningPath.getProgress());
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
                .flatMap(competency -> learningPathRecommendationService.getOrderOfLearningObjectsForCompetency(competency, learningPathUser, false).stream()
                        .map(learningObject -> createLearningPathNavigationObjectDTO(learningObject, false, learningPathUser, competency)))
                .toList();
        return new LearningPathNavigationOverviewDTO(learningObjects);
    }

    private LearningPathNavigationObjectDTO createLearningPathNavigationObjectDTO(LearningObject learningObject, boolean repeatedTest, User user, CourseCompetency competency) {
        if (learningObject == null) {
            return null;
        }

        return LearningPathNavigationObjectDTO.of(learningObject, repeatedTest, learningObjectService.isCompletedByUser(learningObject, user), competency.getId());
    }
}
