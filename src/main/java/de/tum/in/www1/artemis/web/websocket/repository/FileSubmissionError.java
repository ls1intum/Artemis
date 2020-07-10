package de.tum.in.www1.artemis.web.websocket.repository;

import java.io.Serializable;

import de.tum.in.www1.artemis.web.websocket.WebsocketError;

/**
 * Class for marshalling and sending errors encountered when trying to persist file updates received by websocket.
 */
public class FileSubmissionError extends Exception implements Serializable {

    private Long participationId;

    private String fileName;

    FileSubmissionError(Long participationId, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.participationId = participationId;
    }
    FileSubmissionError(Long participationId, String errorMessage) {
        super(errorMessage);
        this.participationId = participationId;
    }

    public Long getParticipationId() {
        return participationId;
    }

    public void setParticipationId(Long participationId) {
        this.participationId = participationId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
