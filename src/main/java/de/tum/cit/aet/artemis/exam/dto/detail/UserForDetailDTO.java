package de.tum.cit.aet.artemis.exam.dto.detail;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * Projection of the examined {@link User} as nested inside the instructor-facing student-exam detail payload
 * (the {@code studentExam} field of {@code StudentExamWithGradeDTO}).
 * <p>
 * Unlike the student-facing conduction / summary projections (which slim the user down to {@code UserNameDTO} because
 * the student only ever sees their own id / name), the instructor detail table identifies the examined student by
 * name, login, e-mail and matriculation number, so those fields are preserved here. {@code visibleRegistrationNumber}
 * is the transient the server populates via {@code User#setVisibleRegistrationNumber()} for instructor callers; it is
 * simply omitted (NON_EMPTY) when not set.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserForDetailDTO(Long id, String login, String name, String email, String visibleRegistrationNumber) {

    /**
     * Converts a User into a UserForDetailDTO.
     *
     * @param user the user to convert
     * @return the converted DTO, or null if the user is null
     */
    public static UserForDetailDTO of(User user) {
        if (user == null) {
            return null;
        }
        return new UserForDetailDTO(user.getId(), user.getLogin(), user.getName(), user.getEmail(), user.getVisibleRegistrationNumber());
    }
}
