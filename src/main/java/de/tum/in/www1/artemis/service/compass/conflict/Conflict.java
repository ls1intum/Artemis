package de.tum.in.www1.artemis.service.compass.conflict;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.compass.assessment.Assessment;

import java.util.Set;
import java.util.UUID;

public class Conflict {
    public Set<Integer> elementIdsInConflict;

    private String id;
    private User initiator;


    public Conflict(Set<Integer> elementIdsInConflict) {
        this.elementIdsInConflict = elementIdsInConflict;
        id = UUID.randomUUID().toString();
    }

    public void addConflictingElement(int elementID) {
        elementIdsInConflict.add(elementID);
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

}
