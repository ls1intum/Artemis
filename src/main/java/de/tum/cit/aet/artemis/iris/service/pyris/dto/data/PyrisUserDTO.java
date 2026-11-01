package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisUserDTO(long id, String firstName, String lastName, String langKey) {

    public PyrisUserDTO(User user) {
        this(user.getId(), user.getFirstName(), user.getLastName(), user.getLangKey());
    }
}
