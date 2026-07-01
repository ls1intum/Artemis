package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseUserDTO(Long id, @Nullable String login, @Nullable String name, @Nullable String visibleRegistrationNumber) {

    public static @Nullable PlagiarismCaseUserDTO fromUser(@Nullable User user) {
        if (user == null) {
            return null;
        }
        return new PlagiarismCaseUserDTO(user.getId(), user.getLogin(), user.getName(), user.getVisibleRegistrationNumber());
    }
}
