package de.tum.cit.aet.artemis.communication.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MailUserDTO(Long id, String login, String name, String email, String visibleRegistrationNumber, String languageKey, String resetKey, Instant resetDate,
        String activationKey) {

    public MailUserDTO(User user) {
        this(user.getId(), user.getLogin(), user.getName(), user.getEmail(), user.getVisibleRegistrationNumber(), user.getLangKey(), user.getResetKey(), user.getResetDate(),
                user.getActivationKey());
    }
}
