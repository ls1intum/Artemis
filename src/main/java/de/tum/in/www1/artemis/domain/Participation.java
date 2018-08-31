package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.enumeration.ParticipationState;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A Participation.
 */
@Entity
@Table(name = "participation", uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "exercise_id", "initialization_state"}))
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Participation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(QuizView.Before.class)
    private Long id;

    @Column(name = "repository_url")
    @JsonView(QuizView.Before.class)
    private String repositoryUrl;

    @Column(name = "build_plan_id")
    @JsonView(QuizView.Before.class)
    private String buildPlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "initialization_state")
    @JsonView(QuizView.Before.class)
    private ParticipationState initializationState;

    @Column(name = "initialization_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime initializationDate;

    @Column(name = "presentation_score")
    private Integer presentationScore;

//    @Column(name = "lti")
//    private Boolean lti;  //TODO: use this in the future


    @OneToMany(mappedBy = "participation")
    @JsonIgnoreProperties({"participation", "submission"})
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private Set<Result> results = new HashSet<>();


    @OneToMany(mappedBy = "participation", cascade = {CascadeType.REMOVE})
    @JsonIgnoreProperties({"participation", "result"})
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Submission> submissions = new HashSet<>();


    @ManyToOne
    @JsonView(QuizView.Before.class)
    private User student;

    // NOTE: Keep default of FetchType.EAGER because most of the times we want
    // to get a participation, we also need the exercise. Dealing with Proxy
    // objects would cause more issues (Subclasses don't work properly for Proxy objects)
    // and the gain from fetching lazy here is minimal
    @ManyToOne
    @JsonView(QuizView.Before.class)
    private Exercise exercise;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public Participation repositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
        return this;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getBuildPlanId() {
        return buildPlanId;
    }

    public Participation buildPlanId(String buildPlanId) {
        this.buildPlanId = buildPlanId;
        return this;
    }

    public void setBuildPlanId(String buildPlanId) {
        this.buildPlanId = buildPlanId;
    }

    public ParticipationState getInitializationState() {
        return initializationState;
    }

    public Participation initializationState(ParticipationState initializationState) {
        this.initializationState = initializationState;
        return this;
    }

    public void setInitializationState(ParticipationState initializationState) {
        this.initializationState = initializationState;
    }

    public ZonedDateTime getInitializationDate() {
        return initializationDate;
    }

    public Participation initializationDate(ZonedDateTime initializationDate) {
        this.initializationDate = initializationDate;
        return this;
    }

    public void setInitializationDate(ZonedDateTime initializationDate) {
        this.initializationDate = initializationDate;
    }

    public Integer getPresentationScore() {
        return presentationScore;
    }

    public Participation presentationScore(Integer presentationScore) {
        this.presentationScore = presentationScore;
        return this;
    }

    public void setPresentationScore(Integer presentationScore) {
        this.presentationScore = presentationScore;
    }

//    public Boolean isLti() {
//        return lti;
//    }
//
//    public Participation lti(Boolean lti) {
//        this.lti = lti;
//        return this;
//    }
//
//    public void setLti(Boolean lti) {
//        this.lti = lti;
//    }

    public Set<Result> getResults() {
        return results;
    }

    public Participation results(Set<Result> results) {
        this.results = results;
        return this;
    }

    public Participation addResult(Result result) {
        this.results.add(result);
        result.setParticipation(this);
        return this;
    }

    public Participation removeResult(Result result) {
        this.results.remove(result);
        result.setParticipation(null);
        return this;
    }

    public void setResults(Set<Result> results) {
        this.results = results;
    }


    public Set<Submission> getSubmissions() {
        return submissions;
    }

    public Participation submissions(Set<Submission> submissions) {
        this.submissions = submissions;
        return this;
    }

    public Participation addSubmissions(Submission submission) {
        this.submissions.add(submission);
        submission.setParticipation(this);
        return this;
    }

    public Participation removeSubmissions(Submission submission) {
        this.submissions.remove(submission);
        submission.setParticipation(null);
        return this;
    }

    public void setSubmissions(Set<Submission> submissions) {
        this.submissions = submissions;
    }


    public User getStudent() {
        return student;
    }

    public Participation student(User user) {
        this.student = user;
        return this;
    }

    public void setStudent(User user) {
        this.student = user;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public Participation exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public URL getRepositoryUrlAsUrl() {
        if (repositoryUrl == null) {
            return null;
        }

        try {
            return new URL(repositoryUrl);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove



    /**
     * Finds the latest result for the participation. Checks if the participation has any results. If there are no results,
     * return null. Otherwise sort the results by completion date and return the first.
     *
     * @return the latest result or null
     */
    public Result findLatestResult() {
        Set<Result> results = this.results;
        if (results == null || results.size() == 0) {
            return null;
        }
        List<Result> sortedResults = results.stream().collect(Collectors.toList());
        Collections.sort(sortedResults, (r1, r2) -> r2.getCompletionDate().compareTo(r1.getCompletionDate()));
        return sortedResults.get(0);
    }

    /**
     * Finds the latest submission for the participation. Checks if the participation has any submissions. If there are no submissions,
     * return null. Otherwise sort the submissions by submission date and return the first.
     *
     * @return the latest submission or null
     */
    public Submission findLatestSubmission() {
        Set<Submission> submissions = this.submissions;
        if (submissions == null || submissions.size() == 0) {
            return null;
        }
        List<Submission> sortedSubmissions = submissions.stream().collect(Collectors.toList());
        Collections.sort(sortedSubmissions, (r1, r2) -> r2.getSubmissionDate().compareTo(r1.getSubmissionDate()));
        return sortedSubmissions.get(0);
    }

    public ModelingSubmission findLatestModelingSubmission() {
        Submission submission = findLatestSubmission();
        submission = (Submission) Hibernate.unproxy(submission);
        if (submission != null && submission instanceof ModelingSubmission) {
            return (ModelingSubmission) submission;
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Participation participation = (Participation) o;
        if (participation.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), participation.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Participation{" +
            "id=" + getId() +
            ", repositoryUrl='" + getRepositoryUrl() + "'" +
            ", buildPlanId='" + getBuildPlanId() + "'" +
            ", initializationState='" + getInitializationState() + "'" +
            ", initializationDate='" + getInitializationDate() + "'" +
            ", presentationScore=" + getPresentationScore() +
            "}";
    }
}
