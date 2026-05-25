package de.tum.cit.aet.artemis.account.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lightweight, cycle-free projection of {@link de.tum.cit.aet.artemis.account.domain.User}
 * intended for embedding inside REST response DTOs.
 * <p>
 * Exists primarily to keep the cyclic JPA relations between {@code User}, {@code Reaction},
 * {@code Post}, {@code AnswerPost}, and {@code TutorialGroupRegistration} out of the JSON shape
 * that crosses the network. Deserializing such a cycle through Jackson can hit the
 * cyclic-reference race in {@code DeserializerCache._createAndCache2} and produce a deterministic
 * {@code "No _valueDeserializer assigned"} failure for the rest of the JVM's lifetime.
 *
 * @param id        the user id
 * @param login     the user login (nullable — not every call site exposes it)
 * @param firstName the user first name (nullable)
 * @param lastName  the user last name (nullable)
 * @param name      the user full name as composed by {@link de.tum.cit.aet.artemis.account.domain.User#getName()}
 * @param imageUrl  the user profile image URL (nullable)
 * @param bot       whether the user is the Iris bot account; serialized as {@code "bot"} to match the wire shape of {@code User.isBot()}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserSummaryDTO(Long id, @Nullable String login, @Nullable String firstName, @Nullable String lastName, @Nullable String name, @Nullable String imageUrl,
        @JsonProperty("bot") boolean bot) {
}
