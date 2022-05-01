package de.tum.in.www1.artemis.domain.exam.monitoring.actions;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;

@Entity
@DiscriminatorValue("CONNECTION_UPDATED")
public class ConnectionUpdatedAction extends ExamAction {

    @Column(name = "connected", nullable = false)
    private boolean connected;

    public ConnectionUpdatedAction(boolean connected) {
        this.connected = connected;
    }

    public ConnectionUpdatedAction() {
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
}
