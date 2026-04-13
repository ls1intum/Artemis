package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;

/**
 * A DTO representing an updated tutorial group with an optional notification text about the update
 *
 * @param tutorialGroup                  the updated tutorial group
 * @param notificationText               the optional notification text
 * @param updateTutorialGroupChannelName whether the tutorial group channel name should be updated with the new tutorial group title or not
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupUpdateDTO(@Valid @NotNull TutorialGroup tutorialGroup, @Size(min = 1, max = 1000) @Nullable String notificationText,
        @Nullable Boolean updateTutorialGroupChannelName) {
}
