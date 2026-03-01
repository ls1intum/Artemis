package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.util.Objects;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;

/**
 * DTO representing a {@link TutorialGroupFreePeriod}.
 *
 * @param id                           the unique identifier of the free period
 * @param start                        start date-time in ISO-8601 format
 * @param end                          end date-time in ISO-8601 format
 * @param reason                       optional reason for the free period
 * @param tutorialGroupConfigurationId the ID of the associated tutorial group configuration
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupFreePeriodDTO(Long id, @NotNull String start, @NotNull String end, String reason, @NotNull Long tutorialGroupConfigurationId) {

    /**
     * Creates a DTO from the given {@link TutorialGroupFreePeriod} entity.
     *
     * @param freePeriod the entity to convert
     * @return a DTO representation of the entity
     * @throws BadRequestAlertException if the entity contains invalid date values or is missing required fields
     */
    public static TutorialGroupFreePeriodDTO of(TutorialGroupFreePeriod freePeriod) {
        Objects.requireNonNull(freePeriod, "tutorialGroupFreePeriod must exist");
        Objects.requireNonNull(freePeriod.getStart(), "Tutorial group free period start date must be set.");
        Objects.requireNonNull(freePeriod.getEnd(), "Tutorial group free period end date must be set.");
        Objects.requireNonNull(freePeriod.getTutorialGroupsConfiguration(), "Tutorial group configuration must be set.");
        return new TutorialGroupFreePeriodDTO(freePeriod.getId(), freePeriod.getStart().toString(), freePeriod.getEnd().toString(), freePeriod.getReason(),
                freePeriod.getTutorialGroupsConfiguration().getId());
    }
}
