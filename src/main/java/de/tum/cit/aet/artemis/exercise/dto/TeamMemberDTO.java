package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * A member of a team as the team management pages render it: the team's students and its owning tutor.
 * <p>
 * The registration number is the transient, already-unmasked one the resource sets where students may see it; where the
 * resource leaves it unset it stays absent, exactly as it did on the entity payload.
 *
 * @param id                        the id of the user
 * @param login                     the login, which the client matches against the logged-in user and the import conflict sets
 * @param name                      the display name
 * @param firstName                 the first name, written to the team export file
 * @param lastName                  the last name, written to the team export file
 * @param email                     the email, shown in the students table and behind the tutor's mailto link
 * @param visibleRegistrationNumber the registration number, where it may be shown
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamMemberDTO(Long id, String login, String name, String firstName, String lastName, String email, String visibleRegistrationNumber) implements Serializable {

    /**
     * Converts a {@link User} into a {@link TeamMemberDTO}.
     *
     * @param user the user to convert (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static TeamMemberDTO of(User user) {
        if (user == null) {
            return null;
        }
        return new TeamMemberDTO(user.getId(), user.getLogin(), user.getName(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getVisibleRegistrationNumber());
    }
}
