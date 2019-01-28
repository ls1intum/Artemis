package de.tum.in.www1.artemis.service.compass.conflict;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.compass.assessment.Assessment;

import java.util.Map;
import java.util.UUID;

public class Conflict {
    public Map<Integer, Assessment> conflictingAssessments;

    private String id;
    private User initiator;


    public Conflict(Map<Integer, Assessment> conflictingAssessments) {
        conflictingAssessments = conflictingAssessments;
        id = UUID.randomUUID().toString();
    }

    public void addConflictingElement(int elementID, Assessment correspondingAssessment) {
        conflictingAssessments.put(elementID, correspondingAssessment);
    }

    public User getInitiator() {
        return initiator;
    }

    public void setInitiator(User initiator) {
        this.initiator = initiator;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
