package de.tum.cit.aet.artemis.fileupload.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * DTO representing user details for file upload contexts (e.g. assessor, note creator).
 *
 * @param id        the ID of the user
 * @param login     the login/username of the user
 * @param name      the full name of the user
 * @param firstName the first name of the user
 * @param lastName  the last name of the user
 * @param imageUrl  the URL to the user's profile image
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadUserDTO(Long id, String login, String name, String firstName, String lastName, String imageUrl) {

    /**
     * Factory method to create a {@link FileUploadUserDTO} from a {@link User} entity.
     *
     * @param user the user entity to map, can be null
     * @return the mapped DTO, or null if the input was null
     */
    public static FileUploadUserDTO of(User user) {
        if (user == null) {
            return null;
        }
        return new FileUploadUserDTO(user.getId(), user.getLogin(), user.getName(), user.getFirstName(), user.getLastName(), user.getImageUrl());
    }
}
