package de.tum.cit.aet.artemis.presentation.dto;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * Minimal DTO for students assigned to presentation assessments.
 */
public record PresentationAssessmentStudentDTO(Long id, @Nullable String login, @Nullable String name, @Nullable String email, @Nullable String visibleRegistrationNumber,
        @Nullable String imageUrl) {

    /**
     * Creates a DTO from a user entity.
     *
     * @param user the user to map
     * @return the mapped DTO
     */
    public static PresentationAssessmentStudentDTO of(User user) {
        return new PresentationAssessmentStudentDTO(user.getId(), user.getLogin(), user.getName(), user.getEmail(), user.getVisibleRegistrationNumber(), user.getImageUrl());
    }
}
