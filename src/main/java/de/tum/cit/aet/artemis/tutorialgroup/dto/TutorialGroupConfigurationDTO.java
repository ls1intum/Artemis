package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupConfigurationDTO(Long id, @NotNull String tutorialPeriodStartInclusive, @NotNull String tutorialPeriodEndInclusive, boolean useTutorialGroupChannels,
        boolean usePublicTutorialGroupChannels, Set<TutorialGroupFreePeriodDTO> tutorialGroupFreePeriods) {

    /**
     * Creates a {@link TutorialGroupConfigurationDTO} from a {@link TutorialGroupsConfiguration} entity.
     * <p>
     * Tutorial group free periods are converted to {@link TutorialGroupFreePeriodDTO}s.
     * Their date and time values are transformed from UTC to the time zone of the
     * tutorial groups configuration.
     *
     * @param config the tutorial groups configuration entity
     * @return a DTO representing the given configuration, or {@code null} if the input is {@code null}
     */
    public static @Nullable TutorialGroupConfigurationDTO of(@Nullable TutorialGroupsConfiguration config) {
        if (config == null) {
            return null;
        }

        var zoneId = zoneOf(config.getTutorialPeriodStartInclusive());

        var freePeriods = config.getTutorialGroupFreePeriods() == null ? Set.<TutorialGroupFreePeriodDTO>of()
                : config.getTutorialGroupFreePeriods().stream().map(p -> TutorialGroupFreePeriodDTO.of(p, zoneId)).collect(Collectors.toSet());

        return new TutorialGroupConfigurationDTO(config.getId(), config.getTutorialPeriodStartInclusive(), config.getTutorialPeriodEndInclusive(),
                config.getUseTutorialGroupChannels(), config.getUsePublicTutorialGroupChannels(), freePeriods);
    }

    /**
     * Creates a {@link TutorialGroupsConfiguration} entity from the given {@link TutorialGroupConfigurationDTO}.
     * <p>
     * This method maps the DTO fields to a new domain object instance including tutorial group free periods.
     * It does not set the associated {@link Course} and does not persist the entity.
     * The caller must handle further validation and persistence.
     *
     * @param dto                 the tutorial group configuration DTO
     * @param configurationZoneId the time zone of the tutorial groups configuration
     * @return a new {@link TutorialGroupsConfiguration} populated with the values from the DTO, or {@code null} if the input is {@code null}
     */
    public static TutorialGroupsConfiguration from(TutorialGroupConfigurationDTO dto, ZoneId configurationZoneId) {
        TutorialGroupsConfiguration configuration = new TutorialGroupsConfiguration();
        configuration.setTutorialPeriodStartInclusive(dto.tutorialPeriodStartInclusive());
        configuration.setTutorialPeriodEndInclusive(dto.tutorialPeriodEndInclusive());
        configuration.setUseTutorialGroupChannels(dto.useTutorialGroupChannels());
        configuration.setUsePublicTutorialGroupChannels(dto.usePublicTutorialGroupChannels());

        if (dto.tutorialGroupFreePeriods() != null) {
            var freePeriods = dto.tutorialGroupFreePeriods().stream().map(freePeriodDto -> {
                var freePeriod = new TutorialGroupFreePeriod();

                if (freePeriodDto.startDate() != null) {
                    freePeriod.setStart(freePeriodDto.startDate().atZone(configurationZoneId).withZoneSameInstant(ZoneId.of("UTC")));
                }
                if (freePeriodDto.endDate() != null) {
                    freePeriod.setEnd(freePeriodDto.endDate().atZone(configurationZoneId).withZoneSameInstant(ZoneId.of("UTC")));
                }

                freePeriod.setReason(freePeriodDto.reason());
                freePeriod.setTutorialGroupsConfiguration(configuration);
                return freePeriod;
            }).collect(Collectors.toSet());

            configuration.setTutorialGroupFreePeriods(freePeriods);
        }

        return configuration;
    }

    /**
     * Determines the time zone of the tutorial groups configuration.
     * <p>
     * The time zone is derived from the ISO 8601 formatted tutorial period start date.
     * If the date string does not contain any zone or offset information,
     * the system default time zone is used as a fallback.
     *
     * @param tutorialPeriodStartInclusive the tutorial groups configuration start date
     * @return the time zone of the configuration
     */
    public static ZoneId zoneOf(String tutorialPeriodStartInclusive) {
        if (tutorialPeriodStartInclusive == null) {
            return ZoneId.systemDefault();
        }
        try {
            return ZonedDateTime.parse(tutorialPeriodStartInclusive).getZone();
        }
        catch (DateTimeParseException ignored) {
            // No zone/offset information present -> fall back
            return ZoneId.systemDefault();
        }
    }
}
