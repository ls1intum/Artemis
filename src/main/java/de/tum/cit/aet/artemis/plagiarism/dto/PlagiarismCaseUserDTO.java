package de.tum.cit.aet.artemis.plagiarism.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseUserDTO(Long id, String login, String name, String visibleRegistrationNumber) {

    public static PlagiarismCaseUserDTO fromUser(User user) {
        if (user == null) {
            return null;
        }
        return new PlagiarismCaseUserDTO(user.getId(), user.getLogin(), user.getName(), user.getVisibleRegistrationNumber());
    }
}
