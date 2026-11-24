package de.tum.cit.aet.artemis.core.dto;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * Generic DTO for displaying a user in a registration modal (search + register flow).
 * Used wherever an instructor needs to search Artemis users and register them into some entity (exam, tutorial group, etc.).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserForRegistrationDTO(Long id, String login, String name, String email, String registrationNumber, String profilePictureUrl, boolean isRegistered) {

    /**
     * Maps a page of users to registration DTOs, marking each user whose id {@code findRegisteredIds} returns as registered.
     *
     * @param users             the page of users found by the search
     * @param findRegisteredIds returns which of the given user ids are already registered; only called for a non-empty page
     * @return the mapped page with the same page index, size and total as {@code users}
     */
    public static Page<UserForRegistrationDTO> pageOf(Page<User> users, Function<List<Long>, Set<Long>> findRegisteredIds) {
        List<Long> userIds = users.getContent().stream().map(User::getId).toList();
        Set<Long> registeredIds = userIds.isEmpty() ? Set.of() : findRegisteredIds.apply(userIds);
        return users.map(user -> new UserForRegistrationDTO(user.getId(), user.getLogin(), user.getName(), user.getEmail(), user.getRegistrationNumber(), user.getImageUrl(),
                registeredIds.contains(user.getId())));
    }
}
