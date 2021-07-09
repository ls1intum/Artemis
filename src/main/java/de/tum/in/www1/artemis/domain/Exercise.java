package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.view.QuizView;
import de.tum.in.www1.artemis.web.rest.dto.DueDateStat;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * An Exercise.
 */
@Entity
@Table(name = "exercise")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "E")
@DiscriminatorOptions(force = true)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// Annotation necessary to distinguish between concrete implementations of Exercise when deserializing from JSON
@JsonSubTypes({ @JsonSubTypes.Type(value = ProgrammingExercise.class, name = "programming"), @JsonSubTypes.Type(value = ModelingExercise.class, name = "modeling"),
        @JsonSubTypes.Type(value = QuizExercise.class, name = "quiz"), @JsonSubTypes.Type(value = TextExercise.class, name = "text"),
        @JsonSubTypes.Type(value = FileUploadExercise.class, name = "file-upload"), })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Exercise extends DomainObject {

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

    @Column(name = "max_points")
    private Double maxPoints;

    @Column(name = "bonus_points")
    private Double bonusPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type")
    private AssessmentType assessmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "included_in_overall_score")
    private IncludedInOverallScore includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;

    @Column(name = "problem_statement")
    @Lob
    private String problemStatement;

    @Column(name = "grading_instructions")
    @Lob
    private String gradingInstructions;

    @ManyToMany(mappedBy = "exercises")
    public Set<LearningGoal> learningGoals = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "exercise_categories", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "categories")
    @JsonView(QuizView.Before.class)
    private Set<String> categories = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty")
    @JsonView(QuizView.Before.class)
    private DifficultyLevel difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode")
    private ExerciseMode mode;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("exercise")
    private TeamAssignmentConfig teamAssignmentConfig;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("exercise")
    private Set<Team> teams = new HashSet<>();

    @Nullable
    @Column(name = "presentation_score_enabled")
    private Boolean presentationScoreEnabled = false;

    @Nullable
    @Column(name = "second_correction_enabled")
    private Boolean secondCorrectionEnabled = false;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private Course course;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private ExerciseGroup exerciseGroup;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = "exercise", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<GradingCriterion> gradingCriteria = new ArrayList<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("exercise")
    private Set<StudentParticipation> studentParticipations = new HashSet<>();

    @OneToMany(mappedBy = "assessedExercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("assessedExercise")
    private Set<TutorParticipation> tutorParticipations = new HashSet<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("exercise")
    private Set<ExampleSubmission> exampleSubmissions = new HashSet<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("exercise")
    private Set<Attachment> attachments = new HashSet<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("exercise")
    private Set<Post> posts = new HashSet<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<ExerciseHint> exerciseHints = new HashSet<>();

    // NOTE: Helpers variable names must be different from Getter name, so that Jackson ignores the @Transient annotation, but Hibernate still respects it
    @Transient
    private DueDateStat numberOfSubmissionsTransient;

    @Transient
    private DueDateStat totalNumberOfAssessmentsTransient;

    @Transient
    private DueDateStat[] numberOfAssessmentsOfCorrectionRoundsTransient;

    @Transient
    private Long numberOfComplaintsTransient;

    @Transient
    private Long numberOfOpenComplaintsTransient;

    @Transient
    private Long numberOfMoreFeedbackRequestsTransient;

    @Transient
    private Long numberOfOpenMoreFeedbackRequestsTransient;

    @Transient
    private Long studentAssignedTeamIdTransient; // id of the team that the logged-in user is assigned to (only relevant if team mode is enabled)

    @Transient
    private boolean studentAssignedTeamIdComputedTransient = false; // set to true if studentAssignedTeamIdTransient was computed for the exercise

    @Transient
    private Long numberOfParticipationsTransient; // used for instructor exam checklist

    @Transient
    private Boolean testRunParticipationsExistTransient;

    @Transient
    private boolean isGradingInstructionFeedbackUsedTransient = false;

    public String getTitle() {
        return title;
    }

    public Exercise title(String title) {
        this.title = title;
        return this;
    }

    /**
     * Sets the title of the exercise
     * all consecutive, trailing or preceding whitespaces are replaced with a single space.
     *
     * @param title the new (unsanitized) title to be set
     */
    public void setTitle(String title) {
        this.title = title != null ? title.strip().replaceAll("\\s+", " ") : null;
    }

    public String getShortName() {
        return shortName;
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
     *
     * @return true if the assessment due date is in the past, otherwise false
     */
    @JsonIgnore
    public boolean isAssessmentDueDateOver() {
        return this.assessmentDueDate == null || ZonedDateTime.now().isAfter(this.assessmentDueDate);
    }

    public Double getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(Double maxPoints) {
        this.maxPoints = maxPoints;
    }

    public Double getBonusPoints() {
        return bonusPoints;
    }

    public void setBonusPoints(Double bonusPoints) {
        this.bonusPoints = bonusPoints;
    }

    public AssessmentType getAssessmentType() {
        return assessmentType;
    }

    public void setAssessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
    }

    public String getProblemStatement() {
        return problemStatement;
    }

    public void setProblemStatement(String problemStatement) {
        this.problemStatement = problemStatement;
    }

    public String getGradingInstructions() {
        return gradingInstructions;
    }

    public void setGradingInstructions(String gradingInstructions) {
        this.gradingInstructions = gradingInstructions;
    }

    public DifficultyLevel getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(DifficultyLevel difficulty) {
        this.difficulty = difficulty;
    }

    public ExerciseMode getMode() {
        return mode;
    }

    public Exercise mode(ExerciseMode mode) {
        this.mode = mode;
        return this;
    }

    public void setMode(ExerciseMode mode) {
        this.mode = mode;
    }

    public TeamAssignmentConfig getTeamAssignmentConfig() {
        return teamAssignmentConfig;
    }

    public void setTeamAssignmentConfig(TeamAssignmentConfig teamAssignmentConfig) {
        this.teamAssignmentConfig = teamAssignmentConfig;
    }

    public Set<Team> getTeams() {
        return teams;
    }

    public void setTeams(Set<Team> teams) {
        this.teams = teams;
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

    public void removeParticipation(StudentParticipation participation) {
        this.studentParticipations.remove(participation);
        participation.setExercise(null);
    }

    public void setStudentParticipations(Set<StudentParticipation> studentParticipations) {
        this.studentParticipations = studentParticipations;
    }

    public Boolean getTestRunParticipationsExist() {
        return testRunParticipationsExistTransient;
    }

    public void setTestRunParticipationsExist(Boolean testRunParticipationsExistTransient) {
        this.testRunParticipationsExistTransient = testRunParticipationsExistTransient;
    }

    /**
     * This method exists for serialization. The utility method getCourseViaExerciseGroupOrCourseMember should be used
     * to get a course for the exercise.
     *
     * @return the course class member
     */
    @JsonInclude
    protected Course getCourse() {
        return course;
    }

    public Exercise course(Course course) {
        this.course = course;
        return this;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    @JsonIgnore
    public boolean isCourseExercise() {
        return this.course != null;
    }

    public ExerciseGroup getExerciseGroup() {
        return exerciseGroup;
    }

    public void setExerciseGroup(ExerciseGroup exerciseGroup) {
        this.exerciseGroup = exerciseGroup;
    }

    @JsonIgnore
    public boolean isExamExercise() {
        return this.exerciseGroup != null;
    }

    /**
     * Utility method to get the course. Get the course over the exerciseGroup, if one was set, otherwise return
     * the course class member
     *
     * @return Course of the exercise
     */
    @JsonIgnore
    public Course getCourseViaExerciseGroupOrCourseMember() {
        if (isExamExercise()) {
            return this.getExerciseGroup().getExam().getCourse();
        }
        else {
            return this.getCourse();
        }
    }

    public Set<ExampleSubmission> getExampleSubmissions() {
        return exampleSubmissions;
    }

    public Exercise addExampleSubmission(ExampleSubmission exampleSubmission) {
        this.exampleSubmissions.add(exampleSubmission);
        exampleSubmission.setExercise(this);
        return this;
    }

    public void removeExampleSubmission(ExampleSubmission exampleSubmission) {
        this.exampleSubmissions.remove(exampleSubmission);
        exampleSubmission.setExercise(null);
    }

    public void setExampleSubmissions(Set<ExampleSubmission> exampleSubmissions) {
        this.exampleSubmissions = exampleSubmissions;
    }

    public Set<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(Set<Attachment> attachments) {
        this.attachments = attachments;
    }

    public Set<Post> getPosts() {
        return posts;
    }

    public void setPosts(Set<Post> posts) {
        this.posts = posts;
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
     * Checks if the due date is in the future. Returns true, if no due date is set.
     *
     * @return true if the due date is in the future, otherwise false
     */
    @JsonIgnore
    public boolean isBeforeDueDate() {
        if (dueDate == null) {
            return true;
        }
        return ZonedDateTime.now().isBefore(dueDate);
    }

    public Set<LearningGoal> getLearningGoals() {
        return learningGoals;
    }

    public void setLearningGoals(Set<LearningGoal> learningGoals) {
        this.learningGoals = learningGoals;
    }

    public boolean isTeamMode() {
        return mode == ExerciseMode.TEAM;
    }

    public Long getNumberOfParticipations() {
        return numberOfParticipationsTransient;
    }

    public void setNumberOfParticipations(Long numberOfParticipationsTransient) {
        this.numberOfParticipationsTransient = numberOfParticipationsTransient;
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
        setGradingCriteria(null);
    }

    /**
     * Find a relevant participation for this exercise (relevancy depends on InitializationState)
     *
     * @param participations the list of available participations
     * @return the found participation, or null, if none exist
     */
    @Nullable
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
     * @param participation           the participation whose results we are considering
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
            var result = submission.getLatestResult();
            // If not the result does not exist or is not assessed yet, we can skip it
            if (result == null || result.getCompletionDate() == null) {
                continue;
            }
            // NOTE: for the dashboard we only use rated results with completion date
            boolean isAssessmentOver = ignoreAssessmentDueDate || getAssessmentDueDate() == null || getAssessmentDueDate().isBefore(ZonedDateTime.now());
            boolean isProgrammingExercise = participation.getExercise() instanceof ProgrammingExercise;
            // Check that submission was submitted in time (rated). For non programming exercises we check if the assessment due date has passed (if set)
            if (Boolean.TRUE.equals(result.isRated()) && (!isProgrammingExercise && isAssessmentOver
                    // For programming exercises we check that the assessment due date has passed (if set) for manual results otherwise we always show the automatic result
                    || isProgrammingExercise && ((result.isManual() && isAssessmentOver) || result.getAssessmentType().equals(AssessmentType.AUTOMATIC)))) {
                // take the first found result that fulfills the above requirements
                if (latestSubmission == null) {
                    latestSubmission = submission;
                }
                // take newer results and thus disregard older ones
                else if (latestSubmission.getLatestResult().getCompletionDate().isBefore(result.getCompletionDate())) {
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
        boolean isAssessmentOver = getAssessmentDueDate() == null || getAssessmentDueDate().isBefore(ZonedDateTime.now());
        if (!isAssessmentOver) {
            return Set.of();
        }
        return participation.getResults().stream().filter(result -> result.getCompletionDate() != null).collect(Collectors.toSet());
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
    public Submission findAppropriateSubmissionByResults(Set<Submission> submissions) {
        List<Submission> submissionsWithRatedResult = new ArrayList<>();
        List<Submission> submissionsWithUnratedResult = new ArrayList<>();
        List<Submission> submissionsWithoutResult = new ArrayList<>();

        for (Submission submission : submissions) {
            Result result = submission.getLatestResult();
            if (result != null) {
                if (Boolean.TRUE.equals(result.isRated())) {
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
            if (this instanceof ProgrammingExercise) {
                // this is an edge case that is treated differently: the student has not submitted before the due date and the client would otherwise think
                // that there is no result for the submission and would display a red trigger button.
                return null;
            }
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

    public Set<TutorParticipation> getTutorParticipations() {
        return tutorParticipations;
    }

    public void setTutorParticipations(Set<TutorParticipation> tutorParticipations) {
        this.tutorParticipations = tutorParticipations;
    }

    public DueDateStat getNumberOfSubmissions() {
        return numberOfSubmissionsTransient;
    }

    public void setNumberOfSubmissions(DueDateStat numberOfSubmissions) {
        this.numberOfSubmissionsTransient = numberOfSubmissions;
    }

    public DueDateStat getTotalNumberOfAssessments() {
        return totalNumberOfAssessmentsTransient;
    }

    public void setTotalNumberOfAssessments(DueDateStat totalNumberOfAssessments) {
        this.totalNumberOfAssessmentsTransient = totalNumberOfAssessments;
    }

    public DueDateStat[] getNumberOfAssessmentsOfCorrectionRounds() {
        return numberOfAssessmentsOfCorrectionRoundsTransient;
    }

    public void setNumberOfAssessmentsOfCorrectionRounds(DueDateStat[] numberOfAssessmentsOfCorrectionRoundsTransient) {
        this.numberOfAssessmentsOfCorrectionRoundsTransient = numberOfAssessmentsOfCorrectionRoundsTransient;
    }

    public Long getNumberOfComplaints() {
        return numberOfComplaintsTransient;
    }

    public void setNumberOfComplaints(Long numberOfComplaints) {
        this.numberOfComplaintsTransient = numberOfComplaints;
    }

    public Long getNumberOfOpenComplaints() {
        return numberOfOpenComplaintsTransient;
    }

    public void setNumberOfOpenComplaints(Long numberOfOpenComplaintsTransient) {
        this.numberOfOpenComplaintsTransient = numberOfOpenComplaintsTransient;
    }

    public Long getNumberOfMoreFeedbackRequests() {
        return numberOfMoreFeedbackRequestsTransient;
    }

    public void setNumberOfMoreFeedbackRequests(Long numberOfMoreFeedbackRequests) {
        this.numberOfMoreFeedbackRequestsTransient = numberOfMoreFeedbackRequests;
    }

    public Long getNumberOfOpenMoreFeedbackRequests() {
        return numberOfOpenMoreFeedbackRequestsTransient;
    }

    public void setNumberOfOpenMoreFeedbackRequests(Long numberOfOpenMoreFeedbackRequests) {
        this.numberOfOpenMoreFeedbackRequestsTransient = numberOfOpenMoreFeedbackRequests;
    }

    /**
     * Checks whether the exercise is released
     *
     * @return boolean
     */
    public boolean isReleased() {
        // Exam
        ZonedDateTime releaseDate;
        if (this.isExamExercise()) {
            releaseDate = this.getExerciseGroup().getExam().getStartDate();
        }
        else {
            releaseDate = getReleaseDate();
        }
        return releaseDate == null || releaseDate.isBefore(ZonedDateTime.now());
    }

    public Long getStudentAssignedTeamId() {
        return studentAssignedTeamIdTransient;
    }

    public void setStudentAssignedTeamId(Long studentAssignedTeamIdTransient) {
        this.studentAssignedTeamIdTransient = studentAssignedTeamIdTransient;
    }

    public boolean isStudentAssignedTeamIdComputed() {
        return studentAssignedTeamIdComputedTransient;
    }

    public void setStudentAssignedTeamIdComputed(boolean studentAssignedTeamIdComputedTransient) {
        this.studentAssignedTeamIdComputedTransient = studentAssignedTeamIdComputedTransient;
    }

    public boolean isGradingInstructionFeedbackUsed() {
        return isGradingInstructionFeedbackUsedTransient;
    }

    public void setGradingInstructionFeedbackUsed(boolean isGradingInstructionFeedbackUsedTransient) {
        this.isGradingInstructionFeedbackUsedTransient = isGradingInstructionFeedbackUsedTransient;
    }

    public Boolean getPresentationScoreEnabled() {
        return presentationScoreEnabled;
    }

    public void setPresentationScoreEnabled(Boolean presentationScoreEnabled) {
        this.presentationScoreEnabled = presentationScoreEnabled;
    }

    public boolean getSecondCorrectionEnabled() {
        return Boolean.TRUE.equals(secondCorrectionEnabled);
    }

    public void setSecondCorrectionEnabled(boolean secondCorrectionEnabled) {
        this.secondCorrectionEnabled = secondCorrectionEnabled;
    }

    public List<GradingCriterion> getGradingCriteria() {
        return gradingCriteria;
    }

    public void addGradingCriteria(GradingCriterion gradingCriterion) {
        this.gradingCriteria.add(gradingCriterion);
        gradingCriterion.setExercise(this);
    }

    public void setGradingCriteria(List<GradingCriterion> gradingCriteria) {
        reconnectCriteriaWithExercise(gradingCriteria);
    }

    private void reconnectCriteriaWithExercise(List<GradingCriterion> gradingCriteria) {
        this.gradingCriteria = gradingCriteria;
        if (gradingCriteria != null) {
            this.gradingCriteria.forEach(gradingCriterion -> gradingCriterion.setExercise(this));
        }
    }

    public IncludedInOverallScore getIncludedInOverallScore() {
        return includedInOverallScore;
    }

    public void setIncludedInOverallScore(IncludedInOverallScore includedInOverallScore) {
        this.includedInOverallScore = includedInOverallScore;
    }

    /**
     * Check whether the exercise has either a course or an exerciseGroup.
     *
     * @param entityName name of the entity
     * @throws BadRequestAlertException if course and exerciseGroup are set or course and exerciseGroup are not set
     */
    public void checkCourseAndExerciseGroupExclusivity(String entityName) throws BadRequestAlertException {
        if (isCourseExercise() == isExamExercise()) {
            throw new BadRequestAlertException("An exercise must have either a course or an exerciseGroup", entityName, "eitherCourseOrExerciseGroupSet");
        }
    }

    /**
     * Return the individual release date for the exercise of the participation's user
     * <p>
     * Currently, exercise start dates are the same for all users
     *
     * @return the time from which on access to the exercise is allowed, for exercises that are not part of an exam, this is just the release date.
     */
    @JsonIgnore
    public ZonedDateTime getIndividualReleaseDate() {
        if (isExamExercise()) {
            return getExerciseGroup().getExam().getStartDate();
        }
        else {
            return getReleaseDate();
        }
    }

    /**
     * returns the number of correction rounds for an exercise. For course exercises this is 1, for exam exercises this must get fetched
     * @return the number of correctionRounds
     */
    @JsonIgnore
    public Integer getNumberOfCorrectionRounds() {
        if (isExamExercise()) {
            return getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam();
        }
        else {
            return 1;
        }
    }

    /** Helper method which does a hard copy of the Grading Criteria
     *
     *
     * @param exerciseImportService@return A clone of the grading criteria list
     */
    public List<GradingCriterion> copyGradingCriteria() {
        List<GradingCriterion> newGradingCriteria = new ArrayList<>();
        for (GradingCriterion originalGradingCriterion : getGradingCriteria()) {
            GradingCriterion newGradingCriterion = new GradingCriterion();
            newGradingCriterion.setExercise(this);
            newGradingCriterion.setTitle(originalGradingCriterion.getTitle());
            newGradingCriterion.setStructuredGradingInstructions(copyGradingInstruction(originalGradingCriterion, newGradingCriterion));
            newGradingCriteria.add(newGradingCriterion);
        }
        return newGradingCriteria;
    }

    /** Helper method which does a hard copy of the Grading Instructions
     *
     * @param originalGradingCriterion The original grading criterion which contains the grading instructions
     * @param newGradingCriterion The cloned grading criterion in which we insert the grading instructions
     * @return A clone of the grading instruction list of the grading criterion
     */
    private List<GradingInstruction> copyGradingInstruction(GradingCriterion originalGradingCriterion, GradingCriterion newGradingCriterion) {
        List<GradingInstruction> newGradingInstructions = new ArrayList<>();
        for (GradingInstruction originalGradingInstruction : originalGradingCriterion.getStructuredGradingInstructions()) {
            GradingInstruction newGradingInstruction = new GradingInstruction();
            newGradingInstruction.setCredits(originalGradingInstruction.getCredits());
            newGradingInstruction.setFeedback(originalGradingInstruction.getFeedback());
            newGradingInstruction.setGradingScale(originalGradingInstruction.getGradingScale());
            newGradingInstruction.setInstructionDescription(originalGradingInstruction.getInstructionDescription());
            newGradingInstruction.setUsageCount(originalGradingInstruction.getUsageCount());
            newGradingInstruction.setGradingCriterion(newGradingCriterion);

            newGradingInstructions.add(newGradingInstruction);
        }
        return newGradingInstructions;
    }

    /**
     * Columns for which we allow a pageable search. For example see {@see de.tum.in.www1.artemis.service.TextExerciseService#getAllOnPageWithSize(PageableSearchDTO, User)}}
     * method. This ensures, that we can't search in columns that don't exist, or we do not want to be searchable.
     */
    public enum ExerciseSearchColumn {

        ID("id"), TITLE("title"), PROGRAMMING_LANGUAGE("programmingLanguage"), COURSE_TITLE("course.title");

        private final String mappedColumnName;

        ExerciseSearchColumn(String mappedColumnName) {
            this.mappedColumnName = mappedColumnName;
        }

        public String getMappedColumnName() {
            return mappedColumnName;
        }
    }

    /**
     * This method is used to validate the dates of an exercise. A date is valid if there is no dueDateError or assessmentDueDateError
     * @throws BadRequestException if the dates are not valid
     */
    public void validateDates() {
        // All fields are optional, so there is no error if none of them is set
        if (getReleaseDate() == null && getDueDate() == null && getAssessmentDueDate() == null) {
            return;
        }
        // at least one is set, so we have to check the two possible errors
        boolean validDates = isBeforeAndNotNull(getReleaseDate(), getDueDate()) && isValidAssessmentDueDate(getReleaseDate(), getDueDate(), getAssessmentDueDate());

        if (!validDates) {
            throw new BadRequestAlertException("The exercise dates are not valid", getTitle(), "noValidDates");
        }
    }

    /**
     * This method is used to validate the assesmentDueDate of an exercise. An assessmentDueDate is valid if it is after the releaseDate and dueDate. A given assesmentDueDate is invalid without an according dueDate
     * @return true if there is no assessmentDueDateError
     */
    private boolean isValidAssessmentDueDate(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate) {
        if (assessmentDueDate == null) {
            return true;
        }
        // There cannot be a assessmentDueDate without dueDate
        if (dueDate == null) {
            return false;
        }
        return isBeforeAndNotNull(dueDate, assessmentDueDate) && isBeforeAndNotNull(releaseDate, assessmentDueDate);
    }

    /**
     * This method is used to validate if the previousDate is before the laterDate.
     * @return true if the previousDate is valid
     */
    private boolean isBeforeAndNotNull(ZonedDateTime previousDate, ZonedDateTime laterDate) {
        if (previousDate == null || laterDate == null) {
            return true;
        }
        return previousDate.isBefore(laterDate);
    }

}
