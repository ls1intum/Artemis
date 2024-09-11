package de.tum.cit.aet.artemis.programming.exceptions;

import de.tum.cit.aet.artemis.core.exception.WebsocketError;

/**
 * A websocket dto for informing the client about a failed submission due to a communication error with the CI system.
 * This happens when a build is triggered without a commit.
 */
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
