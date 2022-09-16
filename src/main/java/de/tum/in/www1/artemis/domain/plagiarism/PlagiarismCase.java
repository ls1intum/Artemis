package de.tum.in.www1.artemis.domain.plagiarism;

import java.time.ZonedDateTime;
import java.util.Set;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.in.www1.artemis.domain.AbstractAuditingEntity;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Post;

@Entity
@Table(name = "plagiarism_case")
public class PlagiarismCase extends AbstractAuditingEntity {

    @ManyToOne
    private Exercise exercise;

    @ManyToOne
    private User student;

    @ManyToOne
    private Team team;

    @OneToOne(mappedBy = "plagiarismCase", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private Post post;

    @JsonIgnoreProperties("plagiarismCase")
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

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    /**
     * If {@link #student} is set, get that student in a set,
     * otherwise get all students in {@link #team} in a set.
     * <p>
     * Note that the returned set only contains student(s) from the one side in the relevant plagiarism situation,
     * you need to check other related @link {@link PlagiarismCase} instances to get all students involved.
     *
     * @return a set of users
     */
    @JsonIgnore
    public Set<User> getStudents() {
        if (student != null) {
            return Set.of(student);
        }
        else if (team != null) {
            return team.getStudents();
        }
        return Set.of();
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
        return "PlagiarismCase{" + "exercise=" + exercise + ", student=" + student + ", post=" + post + ", verdict=" + verdict + ", verdictMessage=" + verdictMessage
                + ", verdictBy=" + verdictBy + ", verdictDate=" + verdictDate + ", verdictPointDeduction=" + verdictPointDeduction + "}";
    }
}
