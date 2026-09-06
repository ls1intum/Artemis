package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.util.ServedFileUrl;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupStudentDTO(@NotNull Long id, @Nullable String name, @Nullable String profilePictureUrl, @NotNull String login, @Nullable String email,
        @Nullable String registrationNumber) {

    /**
     * The profile picture comes out of the column as a filename, so it is turned into the path the client requests it under. The conversion is idempotent.
     */
    public TutorialGroupStudentDTO {
        profilePictureUrl = ServedFileUrl.profilePicture(id, profilePictureUrl);
    }
}
