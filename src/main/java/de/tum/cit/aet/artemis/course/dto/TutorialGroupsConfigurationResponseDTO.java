package de.tum.cit.aet.artemis.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

/**
 * Tutorial-group period and channel settings exposed with an authorized course response.
 *
 * @param id                             the configuration identifier
 * @param tutorialPeriodStartInclusive   the inclusive tutorial-period start in ISO-8601 format
 * @param tutorialPeriodEndInclusive     the inclusive tutorial-period end in ISO-8601 format
 * @param useTutorialGroupChannels       whether tutorial-group channels are created
 * @param usePublicTutorialGroupChannels whether those channels are public
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupsConfigurationResponseDTO(long id, String tutorialPeriodStartInclusive, String tutorialPeriodEndInclusive, boolean useTutorialGroupChannels,
        boolean usePublicTutorialGroupChannels) {

    /**
     * Maps tutorial-group settings without free periods or the course back-reference.
     *
     * @param configuration the tutorial-group configuration
     * @return the tutorial-group configuration response
     */
    public static TutorialGroupsConfigurationResponseDTO of(TutorialGroupsConfiguration configuration) {
        return new TutorialGroupsConfigurationResponseDTO(configuration.getId(), configuration.getTutorialPeriodStartInclusive(), configuration.getTutorialPeriodEndInclusive(),
                configuration.getUseTutorialGroupChannels(), configuration.getUsePublicTutorialGroupChannels());
    }
}
