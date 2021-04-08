package de.tum.in.www1.artemis.web.websocket.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OnlineTeamStudentDTO {

    private String login;

    private Instant lastTypingDate;

    private Instant lastActionDate;

    public OnlineTeamStudentDTO(String login, Instant lastTypingDate, Instant lastActionDate) {
        this.login = login;
        this.lastTypingDate = lastTypingDate;
        this.lastActionDate = lastActionDate;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public Instant getLastTypingDate() {
        return lastTypingDate;
    }

    public void setLastTypingDate(Instant lastTypingDate) {
        this.lastTypingDate = lastTypingDate;
    }

    public Instant getLastActionDate() {
        return lastActionDate;
    }

    public void setLastActionDate(Instant lastActionDate) {
        this.lastActionDate = lastActionDate;
    }
}
