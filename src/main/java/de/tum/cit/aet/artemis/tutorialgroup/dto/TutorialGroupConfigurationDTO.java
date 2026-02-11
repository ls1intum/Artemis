package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupConfigurationDTO(Long id, @NotNull String tutorialPeriodStartInclusive, @NotNull String tutorialPeriodEndInclusive,
        @NotNull Boolean useTutorialGroupChannels, @NotNull Boolean usePublicTutorialGroupChannels, Set<TutorialGroupFreePeriodDTO> tutorialGroupFreePeriods) {

    private static final String ENTITY_NAME = "tutorialGroupsConfiguration";

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupFreePeriodDTO(@NotNull Long id, String start, String end) {

        public static TutorialGroupFreePeriodDTO of(TutorialGroupFreePeriod freePeriod) {
            Objects.requireNonNull(freePeriod, "tutorialGroupFreePeriod must exist");

            return new TutorialGroupFreePeriodDTO(freePeriod.getId(), freePeriod.getStart() != null ? freePeriod.getStart().toString() : null,
                    freePeriod.getEnd() != null ? freePeriod.getEnd().toString() : null);
        }

        public static TutorialGroupFreePeriod from(TutorialGroupFreePeriodDTO dto) {
            Objects.requireNonNull(dto, "tutorialGroupFreePeriodDTO must exist");

            TutorialGroupFreePeriod freePeriod = new TutorialGroupFreePeriod();
            freePeriod.setId(dto.id());
            freePeriod.setStart(dto.start() != null ? ZonedDateTime.parse(dto.start()) : null);
            freePeriod.setEnd(dto.end() != null ? ZonedDateTime.parse(dto.end()) : null);

            return freePeriod;
        }
    }

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

        Set<TutorialGroupFreePeriod> freePeriods = config.getTutorialGroupFreePeriods();
        Set<TutorialGroupFreePeriodDTO> freePeriodDTOs = Set.of();

        if (freePeriods != null && Hibernate.isInitialized(freePeriods)) {
            freePeriodDTOs = freePeriods.stream().map(TutorialGroupFreePeriodDTO::of).collect(Collectors.toSet());
        }
        return new TutorialGroupConfigurationDTO(config.getId(), config.getTutorialPeriodStartInclusive(), config.getTutorialPeriodEndInclusive(),
                config.getUseTutorialGroupChannels(), config.getUsePublicTutorialGroupChannels(), freePeriodDTOs);
    }

    /**
     * Creates a {@link TutorialGroupsConfiguration} entity from the given {@link TutorialGroupConfigurationDTO}.
     *
     * @param dto the tutorial group configuration DTO
     * @return a new {@link TutorialGroupsConfiguration} populated with the values from the DTO
     */
    public static TutorialGroupsConfiguration from(TutorialGroupConfigurationDTO dto) {
        Objects.requireNonNull(dto, "tutorialGroupsConfigurationDTO must exist");

        TutorialGroupsConfiguration configuration = new TutorialGroupsConfiguration();
        configuration.setTutorialPeriodStartInclusive(dto.tutorialPeriodStartInclusive());
        configuration.setTutorialPeriodEndInclusive(dto.tutorialPeriodEndInclusive());
        configuration.setUseTutorialGroupChannels(dto.useTutorialGroupChannels());
        configuration.setUsePublicTutorialGroupChannels(dto.usePublicTutorialGroupChannels());
        if (dto.tutorialGroupFreePeriods() != null) {
            Set<TutorialGroupFreePeriod> freePeriods = dto.tutorialGroupFreePeriods().stream().map(TutorialGroupFreePeriodDTO::from).collect(Collectors.toSet());
            freePeriods.forEach(fp -> fp.setTutorialGroupsConfiguration(configuration));
            configuration.setTutorialGroupFreePeriods(freePeriods);
        }
        else {
            configuration.setTutorialGroupFreePeriods(Set.of());
        }
        return configuration;
    }
}
