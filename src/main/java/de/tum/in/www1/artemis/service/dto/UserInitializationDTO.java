package de.tum.in.www1.artemis.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing a newly created password. If unset, no password was created
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserInitializationDTO {

    private String password;

    public UserInitializationDTO() {
        // Empty constructor needed for Jackson.
    }

    public UserInitializationDTO(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
