package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * How many tutorial group sessions a course holds on one calendar day.
 * <p>
 * The day is a {@link LocalDate} rather than an instant because it is resolved in the time zone of the tutorial groups
 * configuration before it leaves the server. A holiday cancels whatever falls on that day in that zone, so the day is
 * the unit the client reasons about.
 *
 * @param date  the calendar day in the time zone of the tutorial groups configuration
 * @param count how many sessions start on that day, whatever their status
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupSessionCountDTO(LocalDate date, long count) {
}
