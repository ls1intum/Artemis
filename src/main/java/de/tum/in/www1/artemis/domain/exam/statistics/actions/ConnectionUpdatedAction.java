package de.tum.in.www1.artemis.domain.exam.statistics.actions;

import de.tum.in.www1.artemis.domain.exam.statistics.ExamAction;

/**
 * This action shows whether a student has a connection update during the exam or not.
 */
public class ConnectionUpdatedAction extends ExamAction {

    /**
     * Connected is true if the connection is available again, and false if the student has lost his connection.
     */
    private boolean connected;

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
}
