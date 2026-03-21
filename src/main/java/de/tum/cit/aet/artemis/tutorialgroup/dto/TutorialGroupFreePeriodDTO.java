package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;

/**
 * DTO representing a {@link TutorialGroupFreePeriod}.
 *
 * <p>
 * The start and end date-times are normalized to the course time zone
 * and exposed as {@link LocalDateTime} without zone information.
 * Internally, free periods are stored as {@code ZonedDateTime} in UTC,
 * but converted to the course zone before being sent to the client.
 * </p>
 *
 * @param id                           the unique identifier of the free period
 * @param start                        start date-time in the course time zone (without zone information)
 * @param end                          end date-time in the course time zone (without zone information)
 * @param reason                       optional reason for the free period
 * @param tutorialGroupConfigurationId the ID of the associated tutorial group configuration
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupFreePeriodDTO(Long id, @NotNull LocalDateTime start, @NotNull LocalDateTime end, String reason, Long tutorialGroupConfigurationId) {

    /**
     * Creates a DTO from the given {@link TutorialGroupFreePeriod} entity.
     *
     * <p>
     * The stored {@code ZonedDateTime} values are converted to the provided
     * course time zone and then exposed as {@link LocalDateTime}.
     * </p>
     *
     * @param freePeriod the entity to convert
     * @param courseZone the time zone of the course used for normalization
     * @return a DTO representation of the entity
     * @throws NullPointerException if required fields are missing
     */
    public static TutorialGroupFreePeriodDTO of(TutorialGroupFreePeriod freePeriod, ZoneId courseZone) {
        Objects.requireNonNull(freePeriod, "tutorialGroupFreePeriod must exist");
        Objects.requireNonNull(freePeriod.getStart(), "Tutorial group free period start date must be set.");
        Objects.requireNonNull(freePeriod.getEnd(), "Tutorial group free period end date must be set.");
        Objects.requireNonNull(courseZone, "Course time zone must be set.");

        Long tutorialGroupConfigurationId = freePeriod.getTutorialGroupsConfiguration() != null ? freePeriod.getTutorialGroupsConfiguration().getId() : null;

        return new TutorialGroupFreePeriodDTO(freePeriod.getId(), freePeriod.getStart().withZoneSameInstant(courseZone).toLocalDateTime(),
                freePeriod.getEnd().withZoneSameInstant(courseZone).toLocalDateTime(), freePeriod.getReason(), tutorialGroupConfigurationId);
    }
}
