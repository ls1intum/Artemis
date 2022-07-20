package de.tum.in.www1.artemis.domain.exam.monitoring.actions;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;

/**
 * This action shows whether a student has a connection update during the exam or not.
 */
@Entity
@DiscriminatorValue("CONNECTION_UPDATED")
public class ConnectionUpdatedAction extends ExamAction {

    /**
     * Connected is true if the connection is available again, and false if the student has lost his connection.
     */
    @Column(name = "connected")
    private boolean connected;

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
}
