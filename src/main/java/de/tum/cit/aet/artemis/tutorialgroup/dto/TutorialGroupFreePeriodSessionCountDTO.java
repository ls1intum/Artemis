package de.tum.cit.aet.artemis.tutorialgroup.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * How many tutorial group sessions one free period actually covers.
 *
 * Counted by overlap rather than per day, because a free period narrowed to part of a day cancels only the sessions
 * running inside it: a holiday from 09:00 to 10:00 leaves that afternoon's sessions alone, and saying otherwise would
 * overstate what it does.
 *
 * @param freePeriodId the id of the free period the count belongs to
 * @param count        how many of the course's sessions overlap it
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupFreePeriodSessionCountDTO(Long freePeriodId, long count) {
}
