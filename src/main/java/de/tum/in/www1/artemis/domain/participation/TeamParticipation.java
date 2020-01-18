package de.tum.in.www1.artemis.domain.participation;

import java.util.Optional;

import javax.persistence.*;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.Team;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.view.QuizView;

@Entity
@DiscriminatorValue(value = "TP")
public class TeamParticipation extends Participation {

    private static final long serialVersionUID = 1L;

    @Column(name = "presentation_score")
    private Integer presentationScore;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private Team team;

    public Integer getPresentationScore() {
        return presentationScore;
    }

    public TeamParticipation presentationScore(Integer presentationScore) {
        this.presentationScore = presentationScore;
        return this;
    }

    public void setPresentationScore(Integer presentationScore) {
        this.presentationScore = presentationScore;
    }

    public Team getTeam() {
        return team;
    }

    public Participation team(Team team) {
        this.team = team;
        return this;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public TeamParticipation exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    /**
     * Removes the team from the participation, can be invoked to make sure that sensitive information is not sent to the client. E.g. tutors should not see information about
     * the team.
     */
    public void filterSensitiveInformation() {
        setTeam(null);
    }

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
        return "TeamParticipation{" + "presentationScore=" + presentationScore + ", team=" + team + '}';
    }

    @Override
    public Participation copyParticipationId() {
        var participation = new TeamParticipation();
        participation.setId(getId());
        return participation;
    }
}
