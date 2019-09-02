package de.tum.in.www1.artemis.web.websocket.programmingSubmission;

import de.tum.in.www1.artemis.web.websocket.WebsocketError;

public class BuildTriggerWebsocketError extends WebsocketError {

    private Long participationId;

    public BuildTriggerWebsocketError(String error, Long participationId) {
        super(error);
        this.participationId = participationId;
    }

    public Long getParticipationId() {
        return participationId;
    }

    public void setParticipationId(Long participationId) {
        this.participationId = participationId;
    }
}
