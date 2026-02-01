package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupConfigurationDTO(Long id, @NotNull String tutorialPeriodStartInclusive, @NotNull String tutorialPeriodEndInclusive, boolean useTutorialGroupChannels,
        boolean usePublicTutorialGroupChannels) {

    /**
     * Creates a {@link TutorialGroupConfigurationDTO} from a {@link TutorialGroupsConfiguration} entity.
     *
     * @param config the tutorial groups configuration entity
     * @return a DTO representing the given configuration, or {@code null} if the input is {@code null}
     */
    public static @Nullable TutorialGroupConfigurationDTO of(@Nullable TutorialGroupsConfiguration config) {
        if (config == null) {
            return null;
        }

        var course = config.getCourse();
        if (course == null || course.getTimeZone() == null) {
            throw new IllegalStateException("Tutorial group configuration is associated with a course without a time zone");
        }

        return new TutorialGroupConfigurationDTO(config.getId(), config.getTutorialPeriodStartInclusive(), config.getTutorialPeriodEndInclusive(),
                config.getUseTutorialGroupChannels(), config.getUsePublicTutorialGroupChannels());
    }

    /**
     * Creates a {@link TutorialGroupsConfiguration} entity from the given {@link TutorialGroupConfigurationDTO}.
     *
     * @param dto the tutorial group configuration DTO
     * @return a new {@link TutorialGroupsConfiguration} populated with the values from the DTO, or {@code null} if the input is {@code null}
     */
    public static TutorialGroupsConfiguration from(TutorialGroupConfigurationDTO dto) {
        if (dto == null) {
            return null;
        }
        TutorialGroupsConfiguration configuration = new TutorialGroupsConfiguration();
        configuration.setTutorialPeriodStartInclusive(dto.tutorialPeriodStartInclusive());
        configuration.setTutorialPeriodEndInclusive(dto.tutorialPeriodEndInclusive());
        configuration.setUseTutorialGroupChannels(dto.useTutorialGroupChannels());
        configuration.setUsePublicTutorialGroupChannels(dto.usePublicTutorialGroupChannels());
        return configuration;
    }
}
