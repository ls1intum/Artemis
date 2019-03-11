package de.tum.in.www1.artemis.service.compass.conflict;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.compass.assessment.ModelElementAssessment;
import de.tum.in.www1.artemis.service.compass.assessment.Score;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

import java.util.List;
import java.util.UUID;

public class Conflict {
    public UMLElement elementInConflict;
    public Feedback conflictingFeedback;
    public List<Score> scoresInConflict;

    private String id;
    private User initiator;


    public Conflict() {
    }


    public Conflict(UMLElement elementInConflict, Feedback conflictingFeedback, List<Score> scoresInConflict) {
        this.scoresInConflict = scoresInConflict;
        this.elementInConflict = elementInConflict;
        this.conflictingFeedback = conflictingFeedback;
        id = UUID.randomUUID().toString();
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
