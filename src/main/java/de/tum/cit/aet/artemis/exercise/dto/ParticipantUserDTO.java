package de.tum.cit.aet.artemis.exercise.dto;

import java.time.Instant;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * A user as the participation and result payloads of the exercise details and the participation submissions put it on
 * the wire: the student of an individual participation, the members and owner of a team, and the assessor of a result.
 * <p>
 * It repeats every scalar the {@code User} entity serialized, because the exercise details route is reachable with a
 * SCORPIO tool token and its consumer cannot be read in this repository. The entity's associations (authorities,
 * organizations, saved posts, tutorial group registrations, learner profile) are never initialized on these routes, so
 * the entity left them off the wire and this record does not carry them.
 *
 * @param id                        the id of the user
 * @param createdDate               when the account was created
 * @param login                     the login
 * @param participantIdentifier     the login again, as the participant identifier
 * @param firstName                 the first name
 * @param lastName                  the last name
 * @param name                      the display name
 * @param email                     the email address
 * @param imageUrl                  the profile picture
 * @param activated                 whether the account is activated
 * @param langKey                   the preferred language
 * @param visibleRegistrationNumber the registration number, where a caller made it visible
 * @param internal                  whether the account is managed by Artemis
 * @param testUser                  whether the account is a test user
 * @param bot                       whether the account is a bot
 * @param deleted                   whether the account is a legacy tombstone
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipantUserDTO(Long id, @Nullable Instant createdDate, String login, String participantIdentifier, @Nullable String firstName, @Nullable String lastName,
        @Nullable String name, @Nullable String email, @Nullable String imageUrl, boolean activated, @Nullable String langKey, @Nullable String visibleRegistrationNumber,
        boolean internal, boolean testUser, boolean bot, boolean deleted) {

    /**
     * Maps a user, or nothing when it was not loaded.
     *
     * @param user the user, may be {@code null} or an uninitialized proxy
     * @return the user as the payloads carry it, or {@code null}
     */
    public static @Nullable ParticipantUserDTO of(@Nullable User user) {
        if (user == null || !Hibernate.isInitialized(user)) {
            return null;
        }
        return new ParticipantUserDTO(user.getId(), user.getCreatedDate(), user.getLogin(), user.getParticipantIdentifier(), user.getFirstName(), user.getLastName(),
                user.getName(), user.getEmail(), user.getImageUrl(), user.getActivated(), user.getLangKey(), user.getVisibleRegistrationNumber(), user.isInternal(),
                user.isTestUser(), user.isBot(), user.isDeleted());
    }
}
