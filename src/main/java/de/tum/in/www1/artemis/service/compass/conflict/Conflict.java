package de.tum.in.www1.artemis.service.compass.conflict;

import java.util.*;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.compass.assessment.Score;

public class Conflict {
    public String conflictedElementId;
    public Feedback conflictingFeedback;
    public List<Score> scoresInConflict;

    private String id;
    private User initiator;


    public Conflict() {
    }


    public Conflict(String conflictedElementId, Feedback conflictingFeedback, List<Score> scoresInConflict) {
        this.scoresInConflict = scoresInConflict;
        this.conflictedElementId = conflictedElementId;
        this.conflictingFeedback = conflictingFeedback;
        id = UUID.randomUUID().toString();
    }


    public String getElementInConflict() {
        return conflictedElementId;
    }


    public Feedback getConflictingFeedback() {
        return conflictingFeedback;
    }


    public List<Score> getScoresInConflict() {
        return scoresInConflict;
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
