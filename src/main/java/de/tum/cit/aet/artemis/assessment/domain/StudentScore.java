package de.tum.cit.aet.artemis.assessment.domain;

import jakarta.annotation.Nullable;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisAssessment;

@Entity
@DiscriminatorValue("SS")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentScore extends ParticipantScore {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Nullable
    @OneToOne
    @JoinColumn(name = "iris_assessment_id", referencedColumnName = "id", unique = true)
    private IrisAssessment assessment;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public Participant getParticipant() {
        return getUser();
    }

    @Override
    public String toString() {
        Long id = getId();
        Long userId = getUser() != null ? getUser().getId() : null;
        Long exerciseId = getExercise() != null ? getExercise().getId() : null;
        Long lastResultId = getLastResult() != null ? getLastResult().getId() : null;
        Double lastResultScore = getLastScore();
        Long lastRatedResultId = getLastRatedResult() != null ? getLastRatedResult().getId() : null;
        Double lastRatedScore = getLastRatedScore();

        return "StudentScore{" + "id=" + id + ", userId=" + userId + ", exerciseId=" + exerciseId + ", lastResultId=" + lastResultId + ", lastResultScore=" + lastResultScore
                + ", lastRatedResultId=" + lastRatedResultId + ", lastRatedResultScore=" + lastRatedScore + '}';
    }

    @Nullable
    public IrisAssessment getAssessment() {
        return assessment;
    }

    public void setAssessment(@Nullable IrisAssessment assessment) {
        this.assessment = assessment;
    }
}
