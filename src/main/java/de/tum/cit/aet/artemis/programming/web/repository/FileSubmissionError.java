package de.tum.cit.aet.artemis.programming.web.repository;

import java.io.Serializable;

/**
 * Class for marshalling and sending errors encountered when trying to persist file updates received by websocket.
 */
public class FileSubmissionError extends Error implements Serializable {

    private Long participationId;

    private String fileName;

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
