package de.tum.in.www1.artemis.service.dto.exam.monitoring.actions;

import de.tum.in.www1.artemis.service.dto.exam.monitoring.ExamActionDTO;

public class ConnectionUpdatedActionDTO extends ExamActionDTO {

    private boolean connected;

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
}
