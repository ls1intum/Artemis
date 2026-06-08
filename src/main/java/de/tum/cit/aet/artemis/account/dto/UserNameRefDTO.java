package de.tum.cit.aet.artemis.account.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * Minimal, cycle-free projection of {@link User} exposing only the id and display name.
 * <p>
 * Mirrors the pre-refactor wire shape of associations declared as {@code @JsonIncludeProperties({"id","name"})}
 * — currently {@code Reaction.user}. Unlike {@link UserSummaryDTO} (which also carries {@code imageUrl}/{@code bot}
 * to match {@code Posting.author}), this keeps the payload to exactly the two fields the original serialized, so
 * embedding it does not broaden the wire shape the frontend consumes. Like {@link UserSummaryDTO}, it carries no
 * {@code login}/{@code email}/{@code registrationNumber}, preserving the data-minimization guarantee.
 *
 * @param id   the user id
 * @param name the user's display name as composed by {@link User#getName()}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserNameRefDTO(Long id, @Nullable String name) {

    /**
     * Build a {@link UserNameRefDTO} from a {@link User} entity. Returns {@code null} if the input is {@code null}
     * so callers can map nullable references without a separate guard.
     *
     * @param user the user entity to project, may be {@code null}
     * @return the projected reference, or {@code null} when {@code user} is {@code null}
     */
    public static @Nullable UserNameRefDTO from(@Nullable User user) {
        if (user == null) {
            return null;
        }
        return new UserNameRefDTO(user.getId(), user.getName());
    }
}
