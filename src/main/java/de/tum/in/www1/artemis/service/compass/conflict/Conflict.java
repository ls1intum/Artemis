package de.tum.in.www1.artemis.service.compass.conflict;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.compass.assessment.Assessment;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

public class Conflict {
    public HashMap<String, Integer> elementIdsInConflict;

    private String id;
    private User initiator;


    public Conflict(HashMap<String, Integer> elementIdsInConflict) {
        this.elementIdsInConflict = elementIdsInConflict;
        id = UUID.randomUUID().toString();
    }

    public void addConflictingElement(String jsonElementId, int elementID) {
        elementIdsInConflict.put(jsonElementId, elementID);
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
