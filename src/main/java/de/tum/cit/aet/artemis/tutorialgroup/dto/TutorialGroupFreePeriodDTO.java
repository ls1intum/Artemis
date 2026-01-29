package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;

public record TutorialGroupFreePeriodDTO(Long id, LocalDateTime startDate, LocalDateTime endDate, @Nullable String reason) {

    /**
     * Creates a {@link TutorialGroupFreePeriodDTO} from a {@link TutorialGroupFreePeriod} entity.
     * <p>
     * The stored UTC {@link ZonedDateTime} values of the entity are converted
     * to {@link LocalDateTime} using the provided time zone.
     *
     * @param period the tutorial group free period entity
     * @param zoneId the time zone of the tutorial groups configuration
     * @return a DTO representing the given free period
     */
    public static TutorialGroupFreePeriodDTO of(TutorialGroupFreePeriod period, ZoneId zoneId) {
        if (period == null) {
            return null;
        }

        var start = period.getStart() != null ? period.getStart().withZoneSameInstant(zoneId).toLocalDateTime() : null;
        var end = period.getEnd() != null ? period.getEnd().withZoneSameInstant(zoneId).toLocalDateTime() : null;

        return new TutorialGroupFreePeriodDTO(period.getId(), start, end, period.getReason());
    }
}
