package de.tum.in.www1.artemis.domain.participation;

import java.util.Optional;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import org.hibernate.Hibernate;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;

@Entity
@DiscriminatorValue(value = "PP")
public abstract class ParticipantParticipation extends Participation {

    private static final long serialVersionUID = 1L;

    @Column(name = "presentation_score")
    protected Integer presentationScore;

    public Integer getPresentationScore() {
        return presentationScore;
    }

    public ParticipantParticipation presentationScore(Integer presentationScore) {
        this.presentationScore = presentationScore;
        return this;
    }

    public void setPresentationScore(Integer presentationScore) {
        this.presentationScore = presentationScore;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public ParticipantParticipation exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    /**
     * Removes the participant(s) from the participation, can be invoked to make sure that sensitive information is not sent to the client.
     * E.g. tutors should not see information about the student(s).
     */
    public abstract void filterSensitiveInformation();

    private <T extends Submission> Optional<T> findLatestSubmissionOfType(Class<T> submissionType) {
        Optional<Submission> optionalSubmission = findLatestSubmission();
        if (optionalSubmission.isEmpty()) {
            return Optional.empty();
        }

        Submission submission = optionalSubmission.get();
        // This unproxy is necessary to retrieve the right type of submission (e.g. TextSubmission) to be able to
        // compare it with the `submissionType` argument
        submission = (Submission) Hibernate.unproxy(submission);

        if (submissionType.isInstance(submission)) {
            return Optional.of(submissionType.cast(submission));
        }
        else {
            return Optional.empty();
        }
    }

    /**
     * Same functionality as findLatestSubmission() with the difference that this function only returns the found submission, if it is a modeling submission.
     *
     * @return the latest modeling submission or null
     */
    public Optional<ModelingSubmission> findLatestModelingSubmission() {
        return findLatestSubmissionOfType(ModelingSubmission.class);
    }

    /**
     * Same functionality as findLatestSubmission() with the difference that this function only returns the found submission, if it is a text submission.
     *
     * @return the latest text submission or null
     */
    public Optional<TextSubmission> findLatestTextSubmission() {
        return findLatestSubmissionOfType(TextSubmission.class);
    }

    /**
     * Same functionality as findLatestSubmission() with the difference that this function only returns the found submission, if it is a file upload submission.
     *
     * @return the latest file upload submission or null
     */
    public Optional<FileUploadSubmission> findLatestFileUploadSubmission() {
        return findLatestSubmissionOfType(FileUploadSubmission.class);
    }

    @Override
    public String toString() {
        return "ParticipantParticipation{" + "id=" + getId() + ", presentationScore=" + presentationScore + '}';
    }

    @Override
    public abstract Participation copyParticipationId();
}
