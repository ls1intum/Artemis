package de.tum.in.www1.artemis.web.websocket.dto;

import java.time.Instant;

public class OnlineTeamStudentDTO {

    private String login;

    private Instant lastActionDate;

    public OnlineTeamStudentDTO(String login, Instant lastActionDate) {
        this.login = login;
        this.lastActionDate = lastActionDate;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public Instant getLastActionDate() {
        return lastActionDate;
    }

    public void setLastActionDate(Instant lastActionDate) {
        this.lastActionDate = lastActionDate;
    }
}
