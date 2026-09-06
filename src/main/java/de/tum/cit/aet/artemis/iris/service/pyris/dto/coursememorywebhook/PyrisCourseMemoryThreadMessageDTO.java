package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single message within a thread forwarded to Pyris for Course Memory ingestion. The thread is sent
 * ordered oldest&rarr;newest; Pyris uses it to derive the canonical question and the verified answer.
 * <p>
 * Which message holds the verified answer is stated explicitly via {@code isVerifiedAnswer} /
 * {@code resolvesPost} and must never be re-derived from {@code id}: posts and answer posts live in
 * separate tables with independent {@code IDENTITY} sequences, so a root post and one of its answers
 * routinely share a number. {@code id} is therefore namespace-qualified ({@code post-7} /
 * {@code answer-7}) and serves backlinking only.
 *
 * @param id               namespace-qualified id, {@code post-<id>} for the root post and {@code answer-<id>} for replies
 * @param authorRole       one of {@code "student"}, {@code "tutor"} or {@code "iris"}
 * @param content          the message content
 * @param createdAt        ISO-8601 creation timestamp
 * @param isIrisDraft      {@code true} if this message was authored by the Iris bot (AI-generated draft)
 * @param isVerifiedAnswer {@code true} for the single answer whose event triggered this ingestion
 *                             (Trigger A: the just-verified Iris draft; Trigger B: the just-marked answer)
 * @param resolvesPost     {@code true} if this answer carries the durable {@code resolvesPost} flag; a thread
 *                             may contain several, since a post counts as resolved if <em>any</em> answer resolves it
 * @param redacted         {@code true} when the author opted out of AI: {@code content} is empty and the message
 *                             is a placeholder Pyris renders but never quotes. Mirrors
 *                             {@code PyrisAnswerPostDTO#redacted}, so the placeholder text has a single owner on
 *                             the Pyris side rather than being duplicated here
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseMemoryThreadMessageDTO(String id, String authorRole, String content, String createdAt, @JsonProperty("isIrisDraft") boolean isIrisDraft,
        @JsonProperty("isVerifiedAnswer") boolean isVerifiedAnswer, @JsonProperty("resolvesPost") boolean resolvesPost, @JsonProperty("redacted") boolean redacted) {
}
