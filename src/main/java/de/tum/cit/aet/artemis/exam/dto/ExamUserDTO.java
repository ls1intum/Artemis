package de.tum.cit.aet.artemis.exam.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Contains the information about an exam user
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// @formatter:off
public record ExamUserDTO(
        @Size(max = 50) String login,
        @Size(max = 50) String firstName,
        @Size(max = 50) String lastName,
        @Size(max = 10) String registrationNumber,
        @Email @Size(max = 100) String email,
        String studentIdentifier,
        String room,
        String seat,
        boolean didCheckImage,
        boolean didCheckName,
        boolean didCheckRegistrationNumber,
        boolean didCheckLogin,
        @Size(max = 100) String signingImagePath,
        // TODO: Remove everything below here when no longer supporting Exam Checker app version 2.0
        @Nullable Long id,
        @Nullable String actualRoom,
        @Nullable String actualSeat,
        @Nullable String plannedRoom,
        @Nullable String plannedSeat,
        @Nullable String studentImagePath,
        @Nullable ExamUserDetailsDTO user
) {
    // TODO: Remove this DTO when no longer supporting Exam Checker app version 2.0
    /**
     * Contains the details about an exam user
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamUserDetailsDTO(
        @Size(max = 50) String login,
        @Size(max = 50) String name,
        @Nullable @Size(max = 10) String visibleRegistrationNumber,
        Long id
    ) {
        public static ExamUserDetailsDTO fromUser(User user) {
            return new ExamUserDetailsDTO(user.getLogin(), user.getName(), user.getVisibleRegistrationNumber(), user.getId());
        }
    }
}
// @formatter:on
