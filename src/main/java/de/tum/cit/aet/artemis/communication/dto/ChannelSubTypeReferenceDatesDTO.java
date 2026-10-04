package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The dates of the exercise, lecture or exam a channel belongs to.
 * <p>
 * The conversation sidebar highlights a channel whose referenced item is happening around now. Reading those dates off
 * the referenced entity would load it per channel, so they are projected for all channels of a course in one query and
 * handed to the conversion.
 *
 * @param channelId the id of the channel
 * @param startDate the referenced item's release date (exercise) or start date (lecture, exam)
 * @param endDate   the referenced item's due date (exercise) or end date (lecture, exam)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChannelSubTypeReferenceDatesDTO(long channelId, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate) {
}
