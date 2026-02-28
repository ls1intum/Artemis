package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupRegisteredStudentDTO(@NotNull long id, @Nullable String name, @Nullable String profilePictureUrl, @NotNull String login, @Nullable String email,
        @Nullable String registrationNumber) {
}
