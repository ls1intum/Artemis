package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.dto.UserSummaryDTO;
import de.tum.cit.aet.artemis.communication.domain.Reaction;

/**
 * Cycle-free projection of {@link Reaction} for use in REST and websocket responses.
 * <p>
 * Replaces the entity {@code Reaction} on the wire so the {@code Reaction.user → User} and
 * {@code Reaction.post → Post} associations cannot trigger Jackson's cyclic-reference race when the
 * test mapper deserializes them. The back-references to {@code post}/{@code answerPost} are
 * intentionally omitted: a reaction is always returned embedded inside its parent post DTO, so the
 * parent id is implicit. The existing {@code ReactionDTO} is preserved for {@code @RequestBody}
 * payloads on the reactions endpoint, which carries a {@code relatedPostId} and validation
 * constraints inappropriate for outbound payloads.
 *
 * @param id           the reaction id
 * @param user         the user who created the reaction
 * @param emojiId      the emoji identifier
 * @param creationDate when the reaction was created
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ReactionResponseDTO(Long id, @Nullable UserSummaryDTO user, String emojiId, @Nullable ZonedDateTime creationDate) {

    /**
     * Build a {@link ReactionResponseDTO} from a {@link Reaction} entity.
     *
     * @param reaction the reaction to project; must not be {@code null}
     * @return the projected response
     */
    public static ReactionResponseDTO from(Reaction reaction) {
        return new ReactionResponseDTO(reaction.getId(), UserSummaryDTO.from(reaction.getUser()), reaction.getEmojiId(), reaction.getCreationDate());
    }
}
