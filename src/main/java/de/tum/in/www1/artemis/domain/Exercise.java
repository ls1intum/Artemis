package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.view.QuizView;
import de.tum.in.www1.artemis.service.scheduled.QuizScheduleService;

/**
 * A Exercise.
 */
@Entity
@Table(name = "exercise")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "E")
@DiscriminatorOptions(force = true)
// NOTE: Use strict cache to prevent lost updates when updating statistics in semaphore (see StatisticService.java)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// Annotation necessary to distinguish between concrete implementations of Exercise when deserializing from JSON
@JsonSubTypes({ @JsonSubTypes.Type(value = ProgrammingExercise.class, name = "programming"), @JsonSubTypes.Type(value = ModelingExercise.class, name = "modeling"),
        @JsonSubTypes.Type(value = QuizExercise.class, name = "quiz"), @JsonSubTypes.Type(value = TextExercise.class, name = "text"),
        @JsonSubTypes.Type(value = FileUploadExercise.class, name = "file-upload"), })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Exercise implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(QuizView.Before.class)
    private Long id;

    @Column(name = "title")
    @JsonView(QuizView.Before.class)
    private String title;

    @Column(name = "short_name")
    @JsonView(QuizView.Before.class)
    private String shortName;

    @Column(name = "release_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime releaseDate;

    @Column(name = "due_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime dueDate;

    @Column(name = "assessment_due_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime assessmentDueDate;

    @Column(name = "max_score")
    private Double maxScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type")
    private AssessmentType assessmentType;

    @Column(name = "problem_statement")
    @Lob
    private String problemStatement;

    @Column(name = "grading_instructions")
    @Lob
    private String gradingInstructions;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "exercise_categories", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "categories")
    private Set<String> categories = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty")
    private DifficultyLevel difficulty;

    @Nullable
    @Column(name = "presentation_score_enabled")
    private Boolean presentationScoreEnabled = false;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private Course course;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties("exercise")
    private Set<StudentParticipation> studentParticipations = new HashSet<>();

    @OneToMany(mappedBy = "assessedExercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties("assessedExercise")
    private Set<TutorParticipation> tutorParticipations = new HashSet<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties("exercise")
    private Set<ExampleSubmission> exampleSubmissions = new HashSet<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("exercise")
    private Set<Attachment> attachments = new HashSet<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("exercise")
    private Set<StudentQuestion> studentQuestions = new HashSet<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<ExerciseHint> exerciseHints = new HashSet<>();

    // Helpers
    // variable names must be different from Getter name,
    // so that Jackson ignores the @Transient annotation,
    // but Hibernate still respects it
    @Transient
    private Long numberOfParticipationsTransient;

    @Transient
    private Long numberOfAssessmentsTransient;

    @Transient
    private Long numberOfComplaintsTransient;

    @Transient
    private Long numberOfMoreFeedbackRequestsTransient;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public Exercise title(String title) {
        this.title = title;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortName() {
        return shortName;
    }

    public Exercise shortName(String shortName) {
        this.shortName = shortName;
        return this;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public Exercise releaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
        return this;
    }

    public void setReleaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public Exercise dueDate(ZonedDateTime dueDate) {
        this.dueDate = dueDate;
        return this;
    }

    public void setDueDate(ZonedDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public ZonedDateTime getAssessmentDueDate() {
        return assessmentDueDate;
    }

    public Exercise assessmentDueDate(ZonedDateTime assessmentDueDate) {
        this.assessmentDueDate = assessmentDueDate;
        return this;
    }

    public void setAssessmentDueDate(ZonedDateTime assessmentDueDate) {
        this.assessmentDueDate = assessmentDueDate;
    }

    /**
     * Checks if the assessment due date is in the past. Also returns true, if no assessment due date is set.
     * @return true if the assessment due date is in the past, otherwise false
     */
    @JsonIgnore
    public boolean isAssessmentDueDateOver() {
        return this.assessmentDueDate == null || ZonedDateTime.now().isAfter(this.assessmentDueDate);
    }

    public Double getMaxScore() {
        return maxScore;
    }

    public Exercise maxScore(Double maxScore) {
        this.maxScore = maxScore;
        return this;
    }

    public void setMaxScore(Double maxScore) {
        this.maxScore = maxScore;
    }

    public AssessmentType getAssessmentType() {
        return assessmentType;
    }

    public Exercise assessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
        return this;
    }

    public void setAssessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
    }

    public String getProblemStatement() {
        return problemStatement;
    }

    public Exercise problemStatement(String problemStatement) {
        this.problemStatement = problemStatement;
        return this;
    }

    public void setProblemStatement(String problemStatement) {
        this.problemStatement = problemStatement;
    }

    public String getGradingInstructions() {
        return gradingInstructions;
    }

    public Exercise gradingInstructions(String gradingInstructions) {
        this.gradingInstructions = gradingInstructions;
        return this;
    }

    public void setGradingInstructions(String gradingInstructions) {
        this.gradingInstructions = gradingInstructions;
    }

    public DifficultyLevel getDifficulty() {
        return difficulty;
    }

    public Exercise difficulty(DifficultyLevel difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    public void setDifficulty(DifficultyLevel difficulty) {
        this.difficulty = difficulty;
    }

    public Set<String> getCategories() {
        return categories;
    }

    public void setCategories(Set<String> categories) {
        this.categories = categories;
    }

    public Set<StudentParticipation> getStudentParticipations() {
        return studentParticipations;
    }

    public Exercise participations(Set<StudentParticipation> participations) {
        this.studentParticipations = participations;
        return this;
    }

    public Exercise addParticipation(StudentParticipation participation) {
        this.studentParticipations.add(participation);
        participation.setExercise(this);
        return this;
    }

    public Exercise removeParticipation(StudentParticipation participation) {
        this.studentParticipations.remove(participation);
        participation.setExercise(null);
        return this;
    }

    public void setStudentParticipations(Set<StudentParticipation> studentParticipations) {
        this.studentParticipations = studentParticipations;
    }

    public Course getCourse() {
        return course;
    }

    public Exercise course(Course course) {
        this.course = course;
        return this;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Set<ExampleSubmission> getExampleSubmissions() {
        return exampleSubmissions;
    }

    public Exercise exampleSubmissions(Set<ExampleSubmission> exampleSubmissions) {
        this.exampleSubmissions = exampleSubmissions;
        return this;
    }

    public Exercise addExampleSubmission(ExampleSubmission exampleSubmission) {
        this.exampleSubmissions.add(exampleSubmission);
        exampleSubmission.setExercise(this);
        return this;
    }

    public Exercise removeExampleSubmission(ExampleSubmission exampleSubmission) {
        this.exampleSubmissions.remove(exampleSubmission);
        exampleSubmission.setExercise(null);
        return this;
    }

    public void setExampleSubmissions(Set<ExampleSubmission> exampleSubmissions) {
        this.exampleSubmissions = exampleSubmissions;
    }

    public Set<Attachment> getAttachments() {
        return attachments;
    }

    public Exercise attachments(Set<Attachment> attachments) {
        this.attachments = attachments;
        return this;
    }

    public Exercise addAttachment(Attachment attachment) {
        this.attachments.add(attachment);
        attachment.setExercise(this);
        return this;
    }

    public Exercise removeAttachment(Attachment attachment) {
        this.attachments.remove(attachment);
        attachment.setExercise(null);
        return this;
    }

    public void setAttachments(Set<Attachment> attachments) {
        this.attachments = attachments;
    }

    public Set<StudentQuestion> getStudentQuestions() {
        return studentQuestions;
    }

    public Exercise studentQuestions(Set<StudentQuestion> studentQuestions) {
        this.studentQuestions = studentQuestions;
        return this;
    }

    public Exercise addStudentQuestions(StudentQuestion studentQuestion) {
        this.studentQuestions.add(studentQuestion);
        studentQuestion.setExercise(this);
        return this;
    }

    public Exercise removeStudentQuestions(StudentQuestion studentQuestion) {
        this.studentQuestions.remove(studentQuestion);
        studentQuestion.setExercise(null);
        return this;
    }

    public void setStudentQuestions(Set<StudentQuestion> studentQuestions) {
        this.studentQuestions = studentQuestions;
    }

    public Set<ExerciseHint> getExerciseHints() {
        return exerciseHints;
    }

    public void setExerciseHints(Set<ExerciseHint> exerciseHints) {
        this.exerciseHints = exerciseHints;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    public Boolean isEnded() {
        if (getDueDate() == null) {
            return Boolean.FALSE;
        }
        return ZonedDateTime.now().isAfter(getDueDate());
    }

    /**
     * check if students are allowed to see this exercise
     *
     * @return true, if students are allowed to see this exercise, otherwise false
     */
    @JsonView(QuizView.Before.class)
    public Boolean isVisibleToStudents() {
        if (releaseDate == null) {  // no release date means the exercise is visible to students
            return Boolean.TRUE;
        }
        return releaseDate.isBefore(ZonedDateTime.now());
    }

    /**
     * can be invoked to make sure that sensitive information is not sent to the client
     */
    public void filterSensitiveInformation() {
        setGradingInstructions(null);
    }

    /**
     * Find a relevant participation for this exercise (relevancy depends on InitializationState)
     *
     * @param participations the list of available participations
     * @return the found participation, or null, if none exist
     */
    public StudentParticipation findRelevantParticipation(List<StudentParticipation> participations) {
        StudentParticipation relevantParticipation = null;
        for (StudentParticipation participation : participations) {
            if (participation.getExercise() != null && participation.getExercise().equals(this)) {
                if (participation.getInitializationState() == InitializationState.INITIALIZED) {
                    // InitializationState INITIALIZED is preferred
                    // => if we find one, we can return immediately
                    return participation;
                }
                else if (participation.getInitializationState() == InitializationState.INACTIVE) {
                    // InitializationState INACTIVE is also ok
                    // => if we can't find INITIALIZED, we return that one
                    relevantParticipation = participation;
                }
                else if (participation.getExercise() instanceof ModelingExercise || participation.getExercise() instanceof TextExercise
                        || participation.getExercise() instanceof FileUploadExercise) {
                    return participation;
                }
            }
        }
        return relevantParticipation;
    }

    /**
     * Get the latest relevant result from the given participation (rated == true or rated == null) (relevancy depends on Exercise type => this should be overridden by subclasses
     * if necessary)
     *
     * @param participation the participation whose results we are considering
     * @param ignoreAssessmentDueDate defines if assessment due date is ignored for the selected results
     * @return the latest relevant result in the given participation, or null, if none exist
     */
    @Nullable
    public Submission findLatestSubmissionWithRatedResultWithCompletionDate(Participation participation, Boolean ignoreAssessmentDueDate) {
        // for most types of exercises => return latest result (all results are relevant)
        Submission latestSubmission = null;
        // we get the results over the submissions
        if (participation.getSubmissions() == null || participation.getSubmissions().isEmpty()) {
            return null;
        }
        for (var submission : participation.getSubmissions()) {
            var result = submission.getResult();
            if (result == null) {
                continue;
            }
            // NOTE: for the dashboard we only use rated results with completion date
            boolean isAssessmentOver = ignoreAssessmentDueDate || getAssessmentDueDate() == null || getAssessmentDueDate().isBefore(ZonedDateTime.now());
            if (result.getCompletionDate() != null && result.isRated() == Boolean.TRUE && isAssessmentOver) {
                // take the first found result that fulfills the above requirements
                if (latestSubmission == null) {
                    latestSubmission = submission;
                }
                // take newer results and thus disregard older ones
                else if (latestSubmission.getResult().getCompletionDate().isBefore(result.getCompletionDate())) {
                    latestSubmission = submission;
                }
            }
        }
        return latestSubmission;
    }

    /**
     * Find the latest (rated or unrated result) of the given participation. Returns null, if there are no results. Please beware: In many cases you might only want to show rated
     * results.
     *
     * @param participation to find latest result for.
     * @return latest result or null
     */
    public Result findLatestResultWithCompletionDate(Participation participation) {
        if (participation.getResults() == null) {
            return null;
        }
        Optional<Result> latestResult = participation.getResults().stream().filter(result -> result.getCompletionDate() != null).max((result1, result2) -> {
            ZonedDateTime resultDate1 = result1.getCompletionDate();
            ZonedDateTime resultDate2 = result2.getCompletionDate();
            if (resultDate1.equals(resultDate2)) {
                return 0;
            }
            else if (resultDate1.isAfter(resultDate2)) {
                return 1;
            }
            else {
                return -1;
            }
        });
        return latestResult.orElse(null);
    }

    /**
     * Returns all results of an exercise for give participation that have a completion date. If the exercise is restricted like {@link QuizExercise} please override this function
     * with the respective filter. (relevancy depends on Exercise type => this should be overridden by subclasses if necessary)
     *
     * @param participation the participation whose results we are considering
     * @return all results of given participation, or null, if none exist
     */
    public Set<Result> findResultsFilteredForStudents(Participation participation) {
        return participation.getResults().stream().filter(result -> result.getCompletionDate() != null).collect(Collectors.toSet());
    }

    /**
     * Find the participation in participations that belongs to the given exercise that includes the exercise data, plus the found participation with its most recent relevant
     * result. Filter everything else that is not relevant
     *
     * @param participations the set of participations, wherein to search for the relevant participation
     * @param username used to get quiz submission for the user
     * @param isStudent defines if the current user is a student
     */
    public void filterForCourseDashboard(List<StudentParticipation> participations, String username, boolean isStudent) {
        // remove the unnecessary inner course attribute
        setCourse(null);

        // remove the problem statement, which is loaded in the exercise details call
        setProblemStatement(null);

        if (this instanceof ProgrammingExercise) {
            var programmingExercise = (ProgrammingExercise) this;
            programmingExercise.setTestRepositoryUrl(null);
        }

        // get user's participation for the exercise
        StudentParticipation participation = participations != null ? findRelevantParticipation(participations) : null;

        // for quiz exercises also check SubmissionHashMap for submission by this user (active participation)
        // if participation was not found in database
        if (participation == null && this instanceof QuizExercise) {
            QuizSubmission submission = QuizScheduleService.getQuizSubmission(getId(), username);
            if (submission.getSubmissionDate() != null) {
                participation = new StudentParticipation().exercise(this);
                participation.initializationState(InitializationState.INITIALIZED);
            }
        }

        // add relevant submission (relevancy depends on InitializationState) with its result to participation
        if (participation != null) {
            // find the latest submission with a rated result, otherwise the latest submission with
            // an unrated result or alternatively the latest submission without a result
            Set<Submission> submissions = participation.getSubmissions();

            // only transmit the relevant result
            // TODO: we should sync the following two and make sure that we return the correct submission and/or result in all scenarios
            Submission submission = (submissions == null || submissions.isEmpty()) ? null : findAppropriateSubmissionByResults(submissions);
            Submission latestSubmissionWithRatedResult = participation.getExercise().findLatestSubmissionWithRatedResultWithCompletionDate(participation, false);

            Set<Result> results = Set.of();

            if (latestSubmissionWithRatedResult != null && latestSubmissionWithRatedResult.getResult() != null) {
                results = Set.of(latestSubmissionWithRatedResult.getResult());
                // remove inner participation from result
                latestSubmissionWithRatedResult.getResult().setParticipation(null);
                // filter sensitive information about the assessor if the current user is a student
                if (isStudent) {
                    latestSubmissionWithRatedResult.getResult().filterSensitiveInformation();
                }
            }

            // filter sensitive information in submission's result
            if (isStudent && submission != null && submission.getResult() != null) {
                submission.getResult().filterSensitiveInformation();
            }

            // add submission to participation
            if (submission != null) {
                participation.setSubmissions(Set.of(submission));
            }

            participation.setResults(results);

            // remove inner exercise from participation
            participation.setExercise(null);

            // add participation into an array
            setStudentParticipations(Set.of(participation));
        }
    }

    /**
     * Filter for appropriate submission. Relevance in the following order:
     * - submission with rated result
     * - submission with unrated result (late submission)
     * - no submission with any result > latest submission
     *
     * @param submissions that need to be filtered
     * @return filtered submission
     */
    protected Submission findAppropriateSubmissionByResults(Set<Submission> submissions) {
        List<Submission> submissionsWithRatedResult = new ArrayList<>();
        List<Submission> submissionsWithUnratedResult = new ArrayList<>();
        List<Submission> submissionsWithoutResult = new ArrayList<>();

        for (Submission submission : submissions) {
            Result result = submission.getResult();
            if (result != null) {
                if (result.isRated() == Boolean.TRUE) {
                    submissionsWithRatedResult.add(submission);
                }
                else {
                    submissionsWithUnratedResult.add(submission);
                }
            }
            else {
                submissionsWithoutResult.add(submission);
            }
        }

        if (submissionsWithRatedResult.size() > 0) {
            if (submissionsWithRatedResult.size() == 1) {
                return submissionsWithRatedResult.get(0);
            }
            else { // this means with have more than one submission, we want the one with the last submission date
                   // make sure that submissions without submission date do not lead to null pointer exception in the comparison
                return submissionsWithRatedResult.stream().filter(s -> s.getSubmissionDate() != null).max(Comparator.comparing(Submission::getSubmissionDate)).orElse(null);
            }
        }
        else if (submissionsWithUnratedResult.size() > 0) {
            if (submissionsWithUnratedResult.size() == 1) {
                return submissionsWithUnratedResult.get(0);
            }
            else { // this means with have more than one submission, we want the one with the last submission date
                   // make sure that submissions without submission date do not lead to null pointer exception in the comparison
                return submissionsWithUnratedResult.stream().filter(s -> s.getSubmissionDate() != null).max(Comparator.comparing(Submission::getSubmissionDate)).orElse(null);
            }
        }
        else if (submissionsWithoutResult.size() > 0) {
            if (submissionsWithoutResult.size() == 1) {
                return submissionsWithoutResult.get(0);
            }
            else { // this means with have more than one submission, we want the one with the last submission date
                   // make sure that submissions without submission date do not lead to null pointer exception in the comparison
                return submissionsWithoutResult.stream().filter(s -> s.getSubmissionDate() != null).max(Comparator.comparing(Submission::getSubmissionDate)).orElse(null);
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Exercise exercise = (Exercise) o;
        if (exercise.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), exercise.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Exercise{" + "id=" + getId() + ", problemStatement='" + getProblemStatement() + "'" + ", gradingInstructions='" + getGradingInstructions() + "'" + ", title='"
                + getTitle() + "'" + ", shortName='" + getShortName() + "'" + ", releaseDate='" + getReleaseDate() + "'" + ", dueDate='" + getDueDate() + "'"
                + ", assessmentDueDate='" + getAssessmentDueDate() + "'" + ", maxScore=" + getMaxScore() + ", difficulty='" + getDifficulty() + "'" + ", categories='"
                + getCategories() + ", presentationScoreEnabled='" + getPresentationScoreEnabled() + "'" + "}";
    }

    public Set<TutorParticipation> getTutorParticipations() {
        return tutorParticipations;
    }

    public void setTutorParticipations(Set<TutorParticipation> tutorParticipations) {
        this.tutorParticipations = tutorParticipations;
    }

    public Long getNumberOfParticipations() {
        return numberOfParticipationsTransient;
    }

    public void setNumberOfParticipations(Long numberOfParticipations) {
        this.numberOfParticipationsTransient = numberOfParticipations;
    }

    public Long getNumberOfAssessments() {
        return numberOfAssessmentsTransient;
    }

    public void setNumberOfAssessments(Long numberOfAssessments) {
        this.numberOfAssessmentsTransient = numberOfAssessments;
    }

    public Long getNumberOfComplaints() {
        return numberOfComplaintsTransient;
    }

    public void setNumberOfComplaints(Long numberOfComplaints) {
        this.numberOfComplaintsTransient = numberOfComplaints;
    }

    public Long getNumberOfMoreFeedbackRequests() {
        return numberOfMoreFeedbackRequestsTransient;
    }

    public void setNumberOfMoreFeedbackRequests(Long numberOfMoreFeedbackRequests) {
        this.numberOfMoreFeedbackRequestsTransient = numberOfMoreFeedbackRequests;
    }

    public boolean isReleased() {
        ZonedDateTime releaseDate = getReleaseDate();
        return releaseDate == null || releaseDate.isBefore(ZonedDateTime.now());
    }

    public Boolean getPresentationScoreEnabled() {
        return presentationScoreEnabled;
    }

    public void setPresentationScoreEnabled(Boolean presentationScoreEnabled) {
        this.presentationScoreEnabled = presentationScoreEnabled;
    }
}
