package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

/**
 * DTO for creating and updating tutorial groups configuration.
 * For creation, id should be null. For updates, id is required.
 *
 * @param id                             the id of the configuration (null for creation, required for update)
 * @param tutorialPeriodStartInclusive   the start date of the tutorial period in ISO 8601 format
 * @param tutorialPeriodEndInclusive     the end date of the tutorial period in ISO 8601 format
 * @param useTutorialGroupChannels       whether to create tutorial group channels
 * @param usePublicTutorialGroupChannels whether the tutorial group channels should be public
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupsConfigurationDTO(@Nullable Long id, @NotNull String tutorialPeriodStartInclusive, @NotNull String tutorialPeriodEndInclusive,
        @NotNull Boolean useTutorialGroupChannels, @NotNull Boolean usePublicTutorialGroupChannels) {

    /**
     * Creates a new TutorialGroupsConfiguration entity from this DTO.
     * Used for creation of new configurations.
     *
     * @return a new TutorialGroupsConfiguration with the DTO values applied
     */
    public TutorialGroupsConfiguration toEntity() {
        TutorialGroupsConfiguration config = new TutorialGroupsConfiguration();
        config.setTutorialPeriodStartInclusive(tutorialPeriodStartInclusive);
        config.setTutorialPeriodEndInclusive(tutorialPeriodEndInclusive);
        config.setUseTutorialGroupChannels(useTutorialGroupChannels);
        config.setUsePublicTutorialGroupChannels(usePublicTutorialGroupChannels);
        return config;
    }

    /**
     * Applies the DTO values to an existing TutorialGroupsConfiguration entity.
     * Used for updating existing configurations.
     *
     * @param config the configuration to update
     */
    public void applyTo(TutorialGroupsConfiguration config) {
        config.setTutorialPeriodStartInclusive(tutorialPeriodStartInclusive);
        config.setTutorialPeriodEndInclusive(tutorialPeriodEndInclusive);
        config.setUseTutorialGroupChannels(useTutorialGroupChannels);
        config.setUsePublicTutorialGroupChannels(usePublicTutorialGroupChannels);
    }
}
