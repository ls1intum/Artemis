package de.tum.in.www1.artemis.domain.plagiarism;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.AbstractAuditingEntity;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Post;

@Entity
@Table(name = "plagiarism_case")
public class PlagiarismCase extends AbstractAuditingEntity {

    @ManyToOne(targetEntity = Exercise.class)
    private Exercise exercise;

    @ManyToOne(targetEntity = User.class)
    private User student;

    @OneToOne()
    @JoinColumn(name = "post_id")
    private Post post;

    @OneToMany(mappedBy = "plagiarismCase", targetEntity = PlagiarismSubmission.class, fetch = FetchType.LAZY)
    private Set<PlagiarismSubmission<?>> plagiarismSubmissions;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict")
    private PlagiarismVerdict verdict;

    @Lob
    @Column(name = "verdict_message")
    private String verdictMessage;

    @ManyToOne
    private User verdictBy;

    @Column(name = "verdict_date")
    private ZonedDateTime verdictDate;

    @Column(name = "verdict_point_deduction")
    private int verdictPointDeduction;

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

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public Set<PlagiarismSubmission<?>> getPlagiarismSubmissions() {
        return plagiarismSubmissions;
    }

    public void setPlagiarismSubmissions(Set<PlagiarismSubmission<?>> plagiarismSubmissions) {
        this.plagiarismSubmissions = plagiarismSubmissions;
    }

    public PlagiarismVerdict getVerdict() {
        return verdict;
    }

    public void setVerdict(PlagiarismVerdict verdict) {
        this.verdict = verdict;
    }

    public String getVerdictMessage() {
        return verdictMessage;
    }

    public void setVerdictMessage(String verdictMessage) {
        this.verdictMessage = verdictMessage;
    }

    public User getVerdictBy() {
        return verdictBy;
    }

    public void setVerdictBy(User verdictBy) {
        this.verdictBy = verdictBy;
    }

    public ZonedDateTime getVerdictDate() {
        return verdictDate;
    }

    public void setVerdictDate(ZonedDateTime verdictDate) {
        this.verdictDate = verdictDate;
    }

    public int getVerdictPointDeduction() {
        return verdictPointDeduction;
    }

    public void setVerdictPointDeduction(int verdictPointDeduction) {
        this.verdictPointDeduction = verdictPointDeduction;
    }

    @Override
    public String toString() {
        return "PlagiarismCase{}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PlagiarismCase plagiarismCase = (PlagiarismCase) o;

        return Objects.equals(plagiarismCase, o);
    }

    @Override
    public int hashCode() {
        return Objects.hash();
    }
}
