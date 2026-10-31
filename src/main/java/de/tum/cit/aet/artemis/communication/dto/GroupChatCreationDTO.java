package de.tum.cit.aet.artemis.communication.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for creating a group chat. {@code memberLogins} are the logins of the starting members (excluding the requesting user).
 *
 * @param memberLogins the logins of the starting members of the group chat (excluding the requesting user); empty if the request names none
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GroupChatCreationDTO(List<String> memberLogins) {

    public GroupChatCreationDTO(@Nullable List<String> memberLogins) {
        this.memberLogins = memberLogins == null ? List.of() : memberLogins;
    }
}
