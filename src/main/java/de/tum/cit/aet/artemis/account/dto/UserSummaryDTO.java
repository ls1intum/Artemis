package de.tum.cit.aet.artemis.account.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * Lightweight, cycle-free projection of {@link User} intended for embedding inside REST response DTOs.
 * <p>
 * Field selection mirrors the pre-refactor wire shape of {@code Posting.author} — historically
 * declared as {@code @JsonIncludeProperties({"id","name","imageUrl","bot"})}. Keeping the DTO at the
 * same set of fields preserves the data-minimization guarantee the existing tests assert on (no
 * {@code login}, {@code email}, or {@code registrationNumber}) and avoids broadening the wire
 * payload beyond what the frontend already consumes.
 * <p>
 * Exists primarily to keep the cyclic JPA relations between {@code User}, {@code Reaction},
 * {@code Post}, {@code AnswerPost}, and {@code TutorialGroupRegistration} out of the JSON shape
 * that crosses the network. Deserializing such a cycle through Jackson can hit the
 * cyclic-reference race in {@code DeserializerCache._createAndCache2} and produce a deterministic
 * {@code "No _valueDeserializer assigned"} failure for the rest of the JVM's lifetime.
 *
 * @param id       the user id
 * @param name     the user's display name as composed by {@link User#getName()} — first name plus last name, or just first name when last name is missing
 * @param imageUrl the user profile image URL (nullable)
 * @param bot      whether the user is the Iris bot account; serialized as {@code "bot"} to match the wire shape of {@code User.isBot()}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserSummaryDTO(Long id, @Nullable String name, @Nullable String imageUrl, @JsonProperty("bot") boolean bot) {

    /**
     * Build a {@link UserSummaryDTO} from a {@link User} entity. Returns {@code null} if the
     * input is {@code null} so callers can map nullable references without a separate guard.
     *
     * @param user the user entity to project, may be {@code null}
     * @return the projected summary, or {@code null} when {@code user} is {@code null}
     */
    public static @Nullable UserSummaryDTO from(@Nullable User user) {
        if (user == null) {
            return null;
        }
        return new UserSummaryDTO(user.getId(), user.getName(), user.getImageUrl(), user.isBot());
    }
}
