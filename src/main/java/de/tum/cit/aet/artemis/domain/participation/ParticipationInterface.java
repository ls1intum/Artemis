package de.tum.cit.aet.artemis.domain.participation;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.Result;
import de.tum.cit.aet.artemis.domain.Submission;
import de.tum.cit.aet.artemis.domain.enumeration.InitializationState;

public interface ParticipationInterface {

    Long getId();

    void addResult(Result result);

    InitializationState getInitializationState();

    void setInitializationState(InitializationState initializationState);

    ZonedDateTime getInitializationDate();

    void setInitializationDate(ZonedDateTime initializationDate);

    ZonedDateTime getIndividualDueDate();

    void setIndividualDueDate(ZonedDateTime individualDueDate);

    Set<Submission> getSubmissions();

    void addSubmission(Submission submission);

    Exercise getExercise();

    void setExercise(Exercise exercise);

    <T extends Submission> Optional<T> findLatestSubmission();
}
