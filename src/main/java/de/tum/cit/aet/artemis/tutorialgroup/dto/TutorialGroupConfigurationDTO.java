package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.util.Objects;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupConfigurationDTO(Long id, @NotNull String tutorialPeriodStartInclusive, @NotNull String tutorialPeriodEndInclusive,
        @NotNull Boolean useTutorialGroupChannels, @NotNull Boolean usePublicTutorialGroupChannels) {

    private static final String ENTITY_NAME = "tutorialGroupsConfiguration";

    /**
     * Creates a {@link TutorialGroupConfigurationDTO} from a {@link TutorialGroupsConfiguration} entity.
     *
     * @param config the tutorial groups configuration entity
     * @return a DTO representing the given configuration
     */
    public static TutorialGroupConfigurationDTO of(TutorialGroupsConfiguration config) {
        Objects.requireNonNull(config, "tutorialGroupsConfiguration must exist");
        var course = config.getCourse();
        if (course == null || course.getTimeZone() == null) {
            throw new BadRequestAlertException("The Tutorial group configuration has no configured time zone.", ENTITY_NAME, "tutorialGroupConfigurationHasNoTimeZone");
        }

        return new TutorialGroupConfigurationDTO(config.getId(), config.getTutorialPeriodStartInclusive(), config.getTutorialPeriodEndInclusive(),
                config.getUseTutorialGroupChannels(), config.getUsePublicTutorialGroupChannels());
    }

    /**
     * Creates a {@link TutorialGroupsConfiguration} entity from the given {@link TutorialGroupConfigurationDTO}.
     *
     * @param dto the tutorial group configuration DTO
     * @return a new {@link TutorialGroupsConfiguration} populated with the values from the DTO
     */
    public static TutorialGroupsConfiguration from(TutorialGroupConfigurationDTO dto) {
        Objects.requireNonNull(dto, "tutorialGroupsConfiguration must exist");
        TutorialGroupsConfiguration configuration = new TutorialGroupsConfiguration();
        configuration.setTutorialPeriodStartInclusive(dto.tutorialPeriodStartInclusive());
        configuration.setTutorialPeriodEndInclusive(dto.tutorialPeriodEndInclusive());
        configuration.setUseTutorialGroupChannels(dto.useTutorialGroupChannels());
        configuration.setUsePublicTutorialGroupChannels(dto.usePublicTutorialGroupChannels());
        return configuration;
    }
}
