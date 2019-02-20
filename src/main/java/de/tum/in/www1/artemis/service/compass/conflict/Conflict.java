package de.tum.in.www1.artemis.service.compass.conflict;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.compass.assessment.Assessment;
import de.tum.in.www1.artemis.service.compass.assessment.ModelElementAssessment;
import de.tum.in.www1.artemis.service.compass.assessment.Score;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Conflict {
    public UMLElement elementInConflict;
    public ModelElementAssessment conflictingAssessment;
    public List<Score> scoresInConflict;

    private String id;
    private User initiator;


    public Conflict(UMLElement elementInConflict, ModelElementAssessment conflictingAssessment, List<Score> scoresInConflict) {
        this.scoresInConflict = scoresInConflict;
        this.elementInConflict = elementInConflict;
        this.conflictingAssessment = conflictingAssessment;
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
