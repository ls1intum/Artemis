package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;

/**
 * REST response payload for a tutorial group free period — a span during which all sessions in a course are cancelled.
 *
 * @param id     the database id of the free period
 * @param start  the start of the free period (UTC, stored in the database as such)
 * @param end    the end of the free period (UTC, stored in the database as such)
 * @param reason the human-readable reason shown to users (e.g. "Holiday")
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupFreePeriodDTO(Long id, @Nullable ZonedDateTime start, @Nullable ZonedDateTime end, @Nullable String reason) {

    /**
     * Builds a {@link TutorialGroupFreePeriodDTO} from a persisted tutorial group free period.
     *
     * @param freePeriod the free period entity to convert, may be {@code null}
     * @return the converted free period DTO, or {@code null} when no free period is present
     */
    public static TutorialGroupFreePeriodDTO from(TutorialGroupFreePeriod freePeriod) {
        if (freePeriod == null) {
            return null;
        }
        return new TutorialGroupFreePeriodDTO(freePeriod.getId(), freePeriod.getStart(), freePeriod.getEnd(), freePeriod.getReason());
    }
}
