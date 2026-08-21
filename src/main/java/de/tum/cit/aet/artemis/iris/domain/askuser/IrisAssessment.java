package de.tum.cit.aet.artemis.iris.domain.askuser;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * An IrisAssessment represents the current state and results of verifying the understanding of a student.
 */
@Entity
@Table(name = "iris_assessment")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisAssessment extends DomainObject {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnoreProperties({ "groups", "authorities" })
    private User student;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    @JsonIgnoreProperties("studentParticipations")
    private Exercise exercise;

    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "verdict")
    private IrisVerdict verdict;

    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "verdict_review")
    private IrisVerdictReview verdictReview;

    @ElementCollection
    @CollectionTable(name = "iris_reasoning", joinColumns = @JoinColumn(name = "iris_assessment_id"))
    @OrderColumn(name = "position")
    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private List<String> reasoning = new ArrayList<>();

    public IrisAssessment() {
    }

    public IrisAssessment(User student, Exercise exercise) {
        this.student = student;
        this.exercise = exercise;
    }

    @Nullable
    public IrisVerdict getVerdict() {
        return verdict;
    }

    public void setVerdict(@Nullable IrisVerdict verdict) {
        this.verdict = verdict;
    }

    @Nullable
    public IrisVerdictReview getVerdictReview() {
        return verdictReview;
    }

    public void setVerdictReview(@Nullable IrisVerdictReview verdictReview) {
        this.verdictReview = verdictReview;
    }

    public List<String> getReasoning() {
        return reasoning;
    }

    public void setReasoning(List<String> reasoning) {
        this.reasoning = reasoning;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    @Override
    public String toString() {
        Long userId = getStudent() != null ? getStudent().getId() : null;
        Long exerciseId = getExercise() != null ? getExercise().getId() : null;

        return "IrisAssessment{" + "id=" + getId() + ", userId=" + userId + ", exerciseId=" + exerciseId + ", verdict=" + verdict + ", verdictReview=" + verdictReview + '}';
    }
}
