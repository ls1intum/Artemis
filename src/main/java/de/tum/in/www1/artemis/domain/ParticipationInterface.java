package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.enumeration.InitializationState;

public interface ParticipationInterface {

    Long getId();

    void addResult(Result result);

    InitializationState getInitializationState();

    void setInitializationState(InitializationState initializationState);

    ZonedDateTime getInitializationDate();

    void setInitializationDate(ZonedDateTime initializationDate);

    void addSubmissions(Submission submission);
}
