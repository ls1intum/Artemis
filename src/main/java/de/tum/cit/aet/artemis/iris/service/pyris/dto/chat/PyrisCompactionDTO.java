package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A summary Iris wrote of the earlier conversation. Artemis stores it as a SUMMARY message with this JSON content.
 *
 * @param summary                the summary text
 * @param coversThroughMessageId the id of the last message the summary covers
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCompactionDTO(String summary, long coversThroughMessageId) {
}
