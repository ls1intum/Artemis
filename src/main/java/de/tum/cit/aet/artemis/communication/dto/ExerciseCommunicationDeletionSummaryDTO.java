package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing the communication-related deletion summary for an exercise.
 *
 * @param numberOfCommunicationPosts the number of communication posts in the exercise channel
 * @param numberOfAnswerPosts        the number of answer posts in the exercise channel
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseCommunicationDeletionSummaryDTO(long numberOfCommunicationPosts, long numberOfAnswerPosts) {
}
