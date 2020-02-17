package de.tum.in.www1.artemis.domain.participation;

import de.tum.in.www1.artemis.domain.Agent;
import de.tum.in.www1.artemis.domain.Exercise;

public interface AgentParticipation extends ParticipationInterface {

    Integer getPresentationScore();

    void setPresentationScore(Integer presentationScore);

    Agent getAgent();

    void setAgent(Agent agent);

    Exercise getExercise();

    void setExercise(Exercise exercise);

    void filterSensitiveInformation();

    Participation copyParticipationId();
}
