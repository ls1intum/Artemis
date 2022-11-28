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

import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
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
public abstract class Exercise extends BaseExercise implements Completable {

    @Column(name = "allow_complaints_for_automatic_assessments")
    private boolean allowComplaintsForAutomaticAssessments;

    @Column(name = "allow_manual_feedback_requests")
    private boolean allowManualFeedbackRequests;

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
    private Set<LearningGoal> learningGoals = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "exercise_categories", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "categories")
    @JsonView(QuizView.Before.class)
    private Set<String> categories = new HashSet<>();

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
    @JsonIncludeProperties({ "id" })
    private Set<Post> posts = new HashSet<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIncludeProperties({ "id" })
    private Set<PlagiarismCase> plagiarismCases = new HashSet<>();

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

    @Transient
    private Double averageRatingTransient;

    @Transient
    private Long numberOfRatingsTransient;

    @Override
    public boolean isCompletedFor(User user) {
        return this.getStudentParticipations().stream().anyMatch((participation) -> participation.getStudents().contains(user));
    }

    @Override
    public Optional<ZonedDateTime> getCompletionDate(User user) {
        return this.getStudentParticipations().stream().filter((participation) -> participation.getStudents().contains(user)).map(Participation::getInitializationDate).findFirst();
    }

    public boolean getAllowManualFeedbackRequests() {
        return allowManualFeedbackRequests;
    }

    public void setAllowManualFeedbackRequests(boolean allowManualFeedbackRequests) {
        this.allowManualFeedbackRequests = allowManualFeedbackRequests;
    }

    public boolean getAllowComplaintsForAutomaticAssessments() {
        return allowComplaintsForAutomaticAssessments;
    }

    public void setAllowComplaintsForAutomaticAssessments(boolean allowComplaintsForAutomaticAssessments) {
        this.allowComplaintsForAutomaticAssessments = allowComplaintsForAutomaticAssessments;
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

    /**
     * Utility method to get the exam. Get the exam over the exerciseGroup, if one was set, otherwise return null.
     *
     * @return exam, to which the exercise belongs
     */
    @JsonIgnore
    public Exam getExamViaExerciseGroupOrCourseMember() {
        if (isExamExercise()) {
            return this.getExerciseGroup().getExam();
        }
        return null;
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

    public Set<PlagiarismCase> getPlagiarismCases() {
        return plagiarismCases;
    }

    public void setPlagiarismCases(Set<PlagiarismCase> plagiarismCases) {
        this.plagiarismCases = plagiarismCases;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    public Set<LearningGoal> getLearningGoals() {
        return learningGoals;
    }

    public void setLearningGoals(Set<LearningGoal> learningGoals) {
        this.learningGoals = learningGoals;
    }

    public Long getNumberOfParticipations() {
        return numberOfParticipationsTransient;
    }

    public void setNumberOfParticipations(Long numberOfParticipationsTransient) {
        this.numberOfParticipationsTransient = numberOfParticipationsTransient;
    }

    /**
     * can be invoked to make sure that sensitive information is not sent to the client
     */
    public void filterSensitiveInformation() {
        setGradingInstructions(null);
        setGradingCriteria(null);
    }

    /**
     * Find the participation for this exercise
     *
     * @param participations the list of available participations
     * @return the found participation, or null, if none exist
     */
    @Nullable
    public StudentParticipation findParticipation(List<StudentParticipation> participations) {
        for (StudentParticipation participation : participations) {
            if (this.equals(participation.getExercise())) {
                return participation;
            }
        }
        return null;
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
                // this case handles FINISHED participations which typically happen when manual results are involved
                else if (participation.getExercise() instanceof ModelingExercise || participation.getExercise() instanceof TextExercise
                        || participation.getExercise() instanceof FileUploadExercise
                        || (participation.getExercise() instanceof ProgrammingExercise && participation.getInitializationState() == InitializationState.FINISHED)) {
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
                    || isProgrammingExercise && ((result.isManual() && isAssessmentOver) || result.isAutomatic()))) {
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
     * @param participation to find the latest result for.
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
     * - no submission with any result > the latest submission
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

        if (!submissionsWithRatedResult.isEmpty()) {
            if (submissionsWithRatedResult.size() == 1) {
                return submissionsWithRatedResult.get(0);
            }
            else { // this means with have more than one submission, we want the one with the last submission date
                   // make sure that submissions without submission date do not lead to null pointer exception in the comparison
                return submissionsWithRatedResult.stream().filter(s -> s.getSubmissionDate() != null).max(Comparator.naturalOrder()).orElse(null);
            }
        }
        else if (!submissionsWithUnratedResult.isEmpty()) {
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
                return submissionsWithUnratedResult.stream().filter(s -> s.getSubmissionDate() != null).max(Comparator.naturalOrder()).orElse(null);
            }
        }
        else if (!submissionsWithoutResult.isEmpty()) {
            if (submissionsWithoutResult.size() == 1) {
                return submissionsWithoutResult.get(0);
            }
            else { // this means with have more than one submission, we want the one with the last submission date
                   // make sure that submissions without submission date do not lead to null pointer exception in the comparison
                return submissionsWithoutResult.stream().filter(s -> s.getSubmissionDate() != null).max(Comparator.naturalOrder()).orElse(null);
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
        ZonedDateTime releaseDate = getParticipationStartDate();
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

    public Double getAverageRating() {
        return averageRatingTransient;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRatingTransient = averageRating;
    }

    public Long getNumberOfRatings() {
        return numberOfRatingsTransient;
    }

    public void setNumberOfRatings(Long numberOfRatings) {
        this.numberOfRatingsTransient = numberOfRatings;
    }

    @Nullable
    public Boolean getPresentationScoreEnabled() {
        return presentationScoreEnabled;
    }

    public void setPresentationScoreEnabled(@Nullable Boolean presentationScoreEnabled) {
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
     * @throws BadRequestAlertException if the course and exerciseGroup are set or course and exerciseGroup are not set
     */
    public void checkCourseAndExerciseGroupExclusivity(String entityName) throws BadRequestAlertException {
        if (isCourseExercise() == isExamExercise()) {
            throw new BadRequestAlertException("An exercise must have either a course or an exerciseGroup", entityName, "eitherCourseOrExerciseGroupSet");
        }
    }

    /**
     * Return the date from when students can participate in the exercise
     * <p>
     * Currently, exercise start dates are the same for all users
     *
     * @return the time from which on access to the participation is allowed, for exercises that are not part of an exam, this is just the release date or start date.
     */
    @JsonIgnore
    public ZonedDateTime getParticipationStartDate() {
        if (isExamExercise()) {
            return getExerciseGroup().getExam().getStartDate();
        }
        else {
            return getStartDate() != null ? getStartDate() : getReleaseDate();
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
     * Also fills {@code gradingInstructionCopyTracker}.
     *
     * @param gradingInstructionCopyTracker  The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     *
     * @return A clone of the grading criteria list
     */
    public List<GradingCriterion> copyGradingCriteria(Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        List<GradingCriterion> newGradingCriteria = new ArrayList<>();
        for (GradingCriterion originalGradingCriterion : getGradingCriteria()) {
            GradingCriterion newGradingCriterion = new GradingCriterion();
            newGradingCriterion.setExercise(this);
            newGradingCriterion.setTitle(originalGradingCriterion.getTitle());
            newGradingCriterion.setStructuredGradingInstructions(copyGradingInstruction(originalGradingCriterion, newGradingCriterion, gradingInstructionCopyTracker));
            newGradingCriteria.add(newGradingCriterion);
        }
        return newGradingCriteria;
    }

    /** Helper method which does a hard copy of the Grading Instructions
     * Also fills {@code gradingInstructionCopyTracker}.
     *
     * @param originalGradingCriterion The original grading criterion which contains the grading instructions
     * @param newGradingCriterion The cloned grading criterion in which we insert the grading instructions
     * @param gradingInstructionCopyTracker  The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return A clone of the grading instruction list of the grading criterion
     */
    private List<GradingInstruction> copyGradingInstruction(GradingCriterion originalGradingCriterion, GradingCriterion newGradingCriterion,
            Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
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
            gradingInstructionCopyTracker.put(originalGradingInstruction.getId(), newGradingInstruction);
        }
        return newGradingInstructions;
    }

    /**
     * This method is used to validate the dates of an exercise. A date is valid if there is no dueDateError or assessmentDueDateError
     *
     * @throws BadRequestAlertException if the dates are not valid
     */
    public void validateDates() {
        // All fields are optional, so there is no error if none of them is set
        if (getReleaseDate() == null && getStartDate() == null && getDueDate() == null && getAssessmentDueDate() == null && getExampleSolutionPublicationDate() == null) {
            return;
        }
        if (isExamExercise()) {
            throw new BadRequestAlertException("An exam exercise may not have any dates set!", getTitle(), "invalidDatesForExamExercise");
        }

        // at least one is set, so we have to check the three possible errors
        //@formatter:off
        boolean areDatesValid = isNotAfterAndNotNull(getReleaseDate(), getDueDate())
                && isNotAfterAndNotNull(getReleaseDate(), getStartDate())
                && isNotAfterAndNotNull(getStartDate(), getDueDate())
                && isValidAssessmentDueDate(getStartDate(), getDueDate(), getAssessmentDueDate())
                && isValidAssessmentDueDate(getReleaseDate(), getDueDate(), getAssessmentDueDate())
                && isValidExampleSolutionPublicationDate(getStartDate(), getDueDate(), getExampleSolutionPublicationDate(), getIncludedInOverallScore())
                && isValidExampleSolutionPublicationDate(getReleaseDate(), getDueDate(), getExampleSolutionPublicationDate(), getIncludedInOverallScore());
        //@formatter:on

        if (!areDatesValid) {
            throw new BadRequestAlertException("The exercise dates are not valid", getTitle(), "noValidDates");
        }
    }

    /**
     * Validates score settings
     * 1. The maxScore needs to be greater than 0
     * 2. If the specified amount of bonus points is valid depending on the IncludedInOverallScore value
     *
     */
    public void validateScoreSettings() {
        // Check if max score is set
        if (getMaxPoints() == null || getMaxPoints() <= 0) {
            throw new BadRequestAlertException("The max points needs to be greater than 0", "Exercise", "maxScoreInvalid");
        }

        if (getBonusPoints() == null) {
            // make sure the default value is set properly
            setBonusPoints(0.0);
        }

        // Check IncludedInOverallScore
        if (getIncludedInOverallScore() == null) {
            throw new BadRequestAlertException("The IncludedInOverallScore-property must be set", "Exercise", "includedInOverallScoreNotSet");
        }

        if (!getIncludedInOverallScore().validateBonusPoints(getBonusPoints())) {
            throw new BadRequestAlertException("The provided bonus points are not allowed", "Exercise", "bonusPointsInvalid");
        }
    }

    public void validateGeneralSettings() {
        validateScoreSettings();
        validateDates();
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

    public abstract ExerciseType getExerciseType();
}
