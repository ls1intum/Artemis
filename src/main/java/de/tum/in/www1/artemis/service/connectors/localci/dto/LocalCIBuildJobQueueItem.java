package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serial;
import java.io.Serializable;

public class LocalCIBuildJobQueueItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long participationId;

    private String commitHash;

    public LocalCIBuildJobQueueItem(Long participationId, String commitHash) {
        this.participationId = participationId;
        this.commitHash = commitHash;
    }

    public Long getParticipationId() {
        return participationId;
    }

    public void setParticipationId(Long participationId) {
        this.participationId = participationId;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    @Override
    public String toString() {
        return "LocalCIBuildJobQueueItem{" + "participationId='" + participationId + '\'' + ", commitHash='" + commitHash + '\'' + '}';
    }
}
