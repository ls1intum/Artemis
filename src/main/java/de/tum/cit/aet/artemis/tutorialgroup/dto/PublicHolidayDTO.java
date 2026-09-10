package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One public holiday a {@link de.tum.cit.aet.artemis.tutorialgroup.service.PublicHolidayProvider} offers for import.
 *
 * @param date          the day the holiday falls on
 * @param name          the name shown to the instructor, and the reason stored on the free period when imported
 * @param alreadyExists whether the course already has a free period covering this day, so the client can pre-select
 *                          only the holidays that would actually add something
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PublicHolidayDTO(LocalDate date, String name, boolean alreadyExists) {
}
