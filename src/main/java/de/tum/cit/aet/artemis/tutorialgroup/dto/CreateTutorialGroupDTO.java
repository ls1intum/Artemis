package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for creating and updating tutorial groups.
 * Mirrors the TutorialGroup entity structure but only includes required fields to minimize data transport.
 * The nested TeachingAssistantDTO allows the client to send teachingAssistant: { login: "..." } as before.
 *
 * @param id                    the id of the tutorial group (null for creation)
 * @param title                 the title of the tutorial group (max 19 characters)
 * @param teachingAssistant     the teaching assistant (only login is used)
 * @param additionalInformation additional information about the tutorial group
 * @param capacity              the capacity of the tutorial group
 * @param isOnline              whether the tutorial group is online
 * @param language              the language of the tutorial group
 * @param campus                the campus where the tutorial group is held
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateTutorialGroupDTO(@Nullable Long id, @Size(min = 1, max = 19) String title, @Nullable TeachingAssistantDTO teachingAssistant,
        @Nullable String additionalInformation, @Nullable Integer capacity, @Nullable Boolean isOnline, @Nullable String language, @Nullable String campus) {

    /**
     * Minimal DTO for the teaching assistant, only containing the login needed to look up the user.
     *
     * @param login the login of the teaching assistant
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TeachingAssistantDTO(@Nullable String login) {
    }
}
