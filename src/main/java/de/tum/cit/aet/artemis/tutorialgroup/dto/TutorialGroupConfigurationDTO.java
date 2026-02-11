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

/**
 * DTO representing the {@link TutorialGroupsConfiguration}.
 *
 * <p>
 * This DTO is used to transfer tutorial group configuration data between the server and the client.
 * Date values are represented as {@link String} on the API layer to avoid implicit time zone
 * conversions on the client side.
 * </p>
 *
 * <p>
 * The fields {@code tutorialPeriodStartInclusive} and {@code tutorialPeriodEndInclusive}
 * represent local dates (ISO-8601 format, e.g. {@code yyyy-MM-dd}).
 * The server is responsible for interpreting these values in the course time zone.
 * </p>
 *
 * <p>
 * Free periods are optionally included. If the underlying JPA collection is not initialized,
 * an empty set is returned to avoid {@code LazyInitializationException}.
 * </p>
 *
 * @param id                             the unique identifier of the configuration
 * @param tutorialPeriodStartInclusive   start date (inclusive) of the tutorial period in ISO-8601 format
 * @param tutorialPeriodEndInclusive     end date (inclusive) of the tutorial period in ISO-8601 format
 * @param useTutorialGroupChannels       whether tutorial group channels are enabled
 * @param usePublicTutorialGroupChannels whether public tutorial group channels are enabled
 * @param tutorialGroupFreePeriods       optional set of free periods during the tutorial period
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupConfigurationDTO(Long id, @NotNull String tutorialPeriodStartInclusive, @NotNull String tutorialPeriodEndInclusive,
        @NotNull Boolean useTutorialGroupChannels, @NotNull Boolean usePublicTutorialGroupChannels, Set<TutorialGroupFreePeriodDTO> tutorialGroupFreePeriods) {

    private static final String ENTITY_NAME = "tutorialGroupsConfiguration";

    /**
     * DTO representing a {@link TutorialGroupFreePeriod}.
     *
     * <p>
     * Date-time values are serialized as ISO-8601 strings and parsed on the server
     * using {@link ZonedDateTime#parse(CharSequence)}.
     * </p>
     *
     * @param id    the unique identifier of the free period
     * @param start start date-time in ISO-8601 format
     * @param end   end date-time in ISO-8601 format
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupFreePeriodDTO(@NotNull Long id, String start, String end) {

        /**
         * Creates a DTO from the given {@link TutorialGroupFreePeriod} entity.
         *
         * @param freePeriod the entity to convert
         * @return a DTO representation of the entity
         */
        public static TutorialGroupFreePeriodDTO of(TutorialGroupFreePeriod freePeriod) {
            Objects.requireNonNull(freePeriod, "tutorialGroupFreePeriod must exist");

            return new TutorialGroupFreePeriodDTO(freePeriod.getId(), freePeriod.getStart() != null ? freePeriod.getStart().toString() : null,
                    freePeriod.getEnd() != null ? freePeriod.getEnd().toString() : null);
        }

        /**
         * Creates a {@link TutorialGroupFreePeriod} entity from the given DTO.
         *
         * @param dto the DTO to convert
         * @return a new entity populated with the values from the DTO
         */
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
