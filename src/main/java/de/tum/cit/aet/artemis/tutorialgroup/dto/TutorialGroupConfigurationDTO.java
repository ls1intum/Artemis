package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;

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

        var course = config.getCourse();
        if (course == null || course.getTimeZone() == null) {
            throw new BadRequestException("Tutorial group configuration is associated with a course without a time zone");
        }

        ZoneId zoneId = ZoneId.of(course.getTimeZone());
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
}
