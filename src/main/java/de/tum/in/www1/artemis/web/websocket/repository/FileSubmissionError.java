package de.tum.in.www1.artemis.web.websocket.repository;

import de.tum.in.www1.artemis.web.websocket.WebsocketError;

import java.io.Serializable;

/**
 * Class for marshalling and sending errors encountered when trying to persist file updates received by websocket.
 */
public class FileSubmissionError extends WebsocketError implements Serializable {
    private Long participationId;
    private String fileName;

    FileSubmissionError(Long participationId, String fileName, String error) {
        super(error);
        this.participationId = participationId;
        this.fileName = fileName;
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

    public String getError() {
        return error;
    }

    public void setError(String errorMessage) {
        this.error = errorMessage;
    }
}

