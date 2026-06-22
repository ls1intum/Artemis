package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single message within a thread forwarded to Pyris for Course Memory ingestion. The thread is sent
 * ordered oldest&rarr;newest; Pyris uses it to derive the canonical question and the verified answer.
 *
 * @param id          the stringified id of the underlying post / answer post
 * @param authorRole  one of {@code "student"}, {@code "tutor"} or {@code "iris"}
 * @param content     the message content
 * @param createdAt   ISO-8601 creation timestamp
 * @param isIrisDraft {@code true} if this message was authored by the Iris bot (AI-generated draft)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseMemoryThreadMessageDTO(String id, String authorRole, String content, String createdAt, @JsonProperty("isIrisDraft") boolean isIrisDraft) {
}
