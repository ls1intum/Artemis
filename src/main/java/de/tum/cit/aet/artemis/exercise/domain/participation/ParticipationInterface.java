package de.tum.cit.aet.artemis.exercise.domain.participation;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;

public interface ParticipationInterface {

    Long getId();

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

    /**
     * Whether the individual due date marks this participation as an actual feedback request rather than a regular
     * deadline extension. Feedback requests get an individual due date set to the moment of the request, which is
     * always before the exercise's regular due date; extensions are only ever set at or after it (see
     * {@code ParticipationService#updateIndividualDueDates}). Without this distinction, an expired extension would
     * incorrectly be treated as a feedback request.
     *
     * @return true if the individual due date has passed and represents a feedback request, not an extension
     */
    @JsonIgnore
    default boolean isFeedbackRequest() {
        ZonedDateTime individualDueDate = getIndividualDueDate();
        if (individualDueDate == null || individualDueDate.isAfter(ZonedDateTime.now())) {
            return false;
        }
        ZonedDateTime exerciseDueDate = getExercise().getDueDate();
        return exerciseDueDate == null || individualDueDate.isBefore(exerciseDueDate);
    }
}
