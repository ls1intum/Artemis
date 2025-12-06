package de.tum.cit.aet.artemis.exercise.domain;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.DueDateStat;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * An Exercise.
 */
@Entity
@Table(name = "exercise")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "E")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// Annotation necessary to distinguish between concrete implementations of Exercise when deserializing from JSON
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProgrammingExercise.class, name = "programming"),
    @JsonSubTypes.Type(value = ModelingExercise.class, name = "modeling"),
    @JsonSubTypes.Type(value = QuizExercise.class, name = "quiz"),
    @JsonSubTypes.Type(value = TextExercise.class, name = "text"),
    @JsonSubTypes.Type(value = FileUploadExercise.class, name = "file-upload")
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Exercise extends BaseExercise implements LearningObject {

    @Column(name = "allow_complaints_for_automatic_assessments")
    private boolean allowComplaintsForAutomaticAssessments;

    // TODO: rename in a follow up
    @Column(name = "allow_manual_feedback_requests")
    private boolean allowFeedbackRequests;

    @Enumerated(EnumType.STRING)
    @Column(name = "included_in_overall_score")
    private IncludedInOverallScore includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;

    @Column(name = "problem_statement")
    private String problemStatement;

    @Column(name = "grading_instructions")
    private String gradingInstructions;

    @OneToMany(mappedBy = "exercise", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("exercise")
    private Set<CompetencyExerciseLink> competencyLinks = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "exercise_categories", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "categories")
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

    @Column(name = "feedback_suggestion_module") // Athena module name (Athena enabled) or null
    private String feedbackSuggestionModule;

    @ManyToOne
    private Course course;

    @ManyToOne
    private ExerciseGroup exerciseGroup;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = "exercise", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<GradingCriterion> gradingCriteria = new HashSet<>();

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
    private Set<PlagiarismCase> plagiarismCases = new HashSet<>();

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "plagiarism_detection_config_id")
    @JsonIgnoreProperties("exercise")
    private PlagiarismDetectionConfig plagiarismDetectionConfig;

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

    /**
     * Used for receiving the value from client.
     */
    @Transient
    private String channelNameTransient;

    @Override
    public Optional<ZonedDateTime> getCompletionDate(User user) {
        return this.getStudentParticipations().stream().filter((participation) -> participation.getStudents().contains(user)).map(Participation::getInitializationDate).findFirst();
    }

    public boolean getAllowFeedbackRequests() {
        return allowFeedbackRequests;
    }

    public void setAllowFeedbackRequests(boolean allowFeedbackRequests) {
        this.allowFeedbackRequests = allowFeedbackRequests;
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
    @JsonProperty
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
    @Override
    public boolean isExamExercise() {
        return this.exerciseGroup != null;
    }

    @JsonIgnore
    public boolean isTestExamExercise() {
        return isExamExercise() && this.getExam().isTestExam();
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
    public Exam getExam() {
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

    public Set<PlagiarismCase> getPlagiarismCases() {
        return plagiarismCases;
    }

    public void setPlagiarismCases(Set<PlagiarismCase> plagiarismCases) {
        this.plagiarismCases = plagiarismCases;
    }

    public PlagiarismDetectionConfig getPlagiarismDetectionConfig() {
        return plagiarismDetectionConfig;
    }

    public void setPlagiarismDetectionConfig(PlagiarismDetectionConfig plagiarismDetectionConfig) {
        this.plagiarismDetectionConfig = plagiarismDetectionConfig;
    }

    @Override
    public Set<CompetencyExerciseLink> getCompetencyLinks() {
        return competencyLinks;
    }

    public void setCompetencyLinks(Set<CompetencyExerciseLink> competencyLinks) {
        this.competencyLinks = competencyLinks;
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
     * Filters results in all submissions of a student participation based on assessment status and exercise type.
     *
     * <p>
     * This method implements the following filtering logic:
     * </p>
     * <ul>
     * <li>If the assessment is still ongoing:
     * <ul>
     * <li>For {@code TextExercise} or {@code ModelingExercise}: keeps only results with {@code AUTOMATIC_ATHENA} assessment type.</li>
     * <li>For other exercise types: removes all results.</li>
     * </ul>
     * </li>
     * <li>If the assessment is over: keeps only results that have a completion date.</li>
     * </ul>
     *
     * <p>
     * This filtering happens in-place by modifying the results list of each submission.
     * </p>
     * <p>
     * Override this method in subclasses if different filtering behavior is required for specific exercise types.
     * </p>
     *
     * @param participation the participation containing submissions to filter
     */
    public void filterResultsForStudents(Participation participation) {
        boolean isAssessmentOver = getAssessmentDueDate() == null || getAssessmentDueDate().isBefore(ZonedDateTime.now());

        participation.getSubmissions().forEach(submission -> {
            List<Result> results = submission.getResults();
            if (results != null && !results.isEmpty()) {
                if (!isAssessmentOver) {
                    // For assessment that's not over yet
                    if (this instanceof TextExercise || this instanceof ModelingExercise) {
                        // Keep only AUTOMATIC_ATHENA results, set others to null
                        results.removeIf(result -> result.getAssessmentType() != AssessmentType.AUTOMATIC_ATHENA);
                    }
                    else {
                        // Clear all results if not TextExercise or ModelingExercise
                        results.clear();
                    }
                }
                else {
                    // For completed assessments, remove results without completion date
                    results.removeIf(result -> result.getCompletionDate() == null);
                }
            }
        });
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
     * @return true if the exercise is released (i.e. now is after the release date or the release date is null), false otherwise
     */
    @JsonIgnore
    public boolean isReleased() {
        ZonedDateTime releaseDate = getParticipationStartDate();
        return releaseDate == null || releaseDate.isBefore(ZonedDateTime.now());
    }

    public Long getStudentAssignedTeamId() {
        return studentAssignedTeamIdTransient;
    }

    public void setStudentAssignedTeamId(Long studentAssignedTeamIdTransient) {
        this.studentAssignedTeamIdTransient = studentAssignedTeamIdTransient;
    }

    // TODO: do we really need this information in all places in the client? I doubt this, we should probably JsonIgnore this in most cases
    public boolean isStudentAssignedTeamIdComputed() {
        return studentAssignedTeamIdComputedTransient;
    }

    public void setStudentAssignedTeamIdComputed(boolean studentAssignedTeamIdComputedTransient) {
        this.studentAssignedTeamIdComputedTransient = studentAssignedTeamIdComputedTransient;
    }

    // TODO: do we really need this information in all places in the client? I doubt this, we should probably JsonIgnore this in most cases
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

    public String getChannelName() {
        return channelNameTransient;
    }

    public void setChannelName(String channelNameTransient) {
        this.channelNameTransient = channelNameTransient;
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

    public String getFeedbackSuggestionModule() {
        return feedbackSuggestionModule;
    }

    public void setFeedbackSuggestionModule(String feedbackSuggestionModule) {
        this.feedbackSuggestionModule = feedbackSuggestionModule;
    }

    public boolean areFeedbackSuggestionsEnabled() {
        return feedbackSuggestionModule != null;
    }

    public Set<GradingCriterion> getGradingCriteria() {
        return gradingCriteria;
    }

    public void addGradingCriteria(GradingCriterion gradingCriterion) {
        this.gradingCriteria.add(gradingCriterion);
        gradingCriterion.setExercise(this);
    }

    public void setGradingCriteria(Set<GradingCriterion> gradingCriteria) {
        reconnectCriteriaWithExercise(gradingCriteria);
    }

    private void reconnectCriteriaWithExercise(Set<GradingCriterion> gradingCriteria) {
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
    @Nullable
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
     *
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

    /**
     * Helper method which does a hard copy of the Grading Criteria
     * Also fills {@code gradingInstructionCopyTracker}.
     *
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return A clone of the grading criteria list
     */
    public Set<GradingCriterion> copyGradingCriteria(Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        Set<GradingCriterion> newGradingCriteria = new HashSet<>();
        for (GradingCriterion originalGradingCriterion : getGradingCriteria()) {
            GradingCriterion newGradingCriterion = new GradingCriterion();
            newGradingCriterion.setExercise(this);
            newGradingCriterion.setTitle(originalGradingCriterion.getTitle());
            newGradingCriterion.setStructuredGradingInstructions(copyGradingInstruction(originalGradingCriterion, newGradingCriterion, gradingInstructionCopyTracker));
            newGradingCriteria.add(newGradingCriterion);
        }
        return newGradingCriteria;
    }

    /**
     * Helper method which does a hard copy of the Grading Instructions
     * Also fills {@code gradingInstructionCopyTracker}.
     *
     * @param originalGradingCriterion      The original grading criterion which contains the grading instructions
     * @param newGradingCriterion           The cloned grading criterion in which we insert the grading instructions
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return A clone of the grading instruction list of the grading criterion
     */
    private Set<GradingInstruction> copyGradingInstruction(GradingCriterion originalGradingCriterion, GradingCriterion newGradingCriterion,
            Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        Set<GradingInstruction> newGradingInstructions = new HashSet<>();
        for (GradingInstruction originalGradingInstruction : originalGradingCriterion.getStructuredGradingInstructions()) {
            final var newGradingInstruction = copyGradingInstruction(newGradingCriterion, originalGradingInstruction);
            newGradingInstructions.add(newGradingInstruction);
            gradingInstructionCopyTracker.put(originalGradingInstruction.getId(), newGradingInstruction);
        }
        return newGradingInstructions;
    }

    private static GradingInstruction copyGradingInstruction(GradingCriterion newGradingCriterion, GradingInstruction originalGradingInstruction) {
        GradingInstruction newGradingInstruction = new GradingInstruction();
        newGradingInstruction.setCredits(originalGradingInstruction.getCredits());
        newGradingInstruction.setFeedback(originalGradingInstruction.getFeedback());
        newGradingInstruction.setGradingScale(originalGradingInstruction.getGradingScale());
        newGradingInstruction.setInstructionDescription(originalGradingInstruction.getInstructionDescription());
        newGradingInstruction.setUsageCount(originalGradingInstruction.getUsageCount());
        newGradingInstruction.setGradingCriterion(newGradingCriterion);
        return newGradingInstruction;
    }

    /**
     * Checks whether students should be able to see the example solution.
     *
     * @return true if example solution publication date is in the past, false otherwise (including null case).
     */
    @JsonIgnore
    public boolean isExampleSolutionPublished() {
        ZonedDateTime exampleSolutionPublicationDate = this.isExamExercise() ? this.getExam().getExampleSolutionPublicationDate() : this.getExampleSolutionPublicationDate();
        return exampleSolutionPublicationDate != null && ZonedDateTime.now().isAfter(exampleSolutionPublicationDate);
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
     * 1. The maxScore needs to be greater than 0. If maxScore is null or <= 0, it is set to the default value 1.0.
     * 2. If the specified amount of bonus points is valid depending on the IncludedInOverallScore value
     */
    public void validateScoreSettings() {
        // Check if max score is set
        if (getMaxPoints() == null || getMaxPoints() <= 0) {
            // make sure the default value is set properly
            setMaxPoints(1.0);
        }

        if (getBonusPoints() == null || getBonusPoints() < 0) {
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
        validateExamExerciseIncludedInScoreCompletely();
    }

    private void validateExamExerciseIncludedInScoreCompletely() {
        if (isExamExercise() && includedInOverallScore == IncludedInOverallScore.NOT_INCLUDED) {
            throw new BadRequestAlertException("An exam exercise must be included in the score.", getTitle(), "examExerciseNotIncludedInScore");
        }
    }

    public abstract ExerciseType getExerciseType();

    public abstract String getType();

    /**
     * Disconnects child entities from the exercise.
     * <p>
     * Just setting the collections to {@code null} breaks the automatic orphan removal and change detection in the database.
     */
    public void disconnectRelatedEntities() {
        Stream.of(teams, gradingCriteria, studentParticipations, tutorParticipations, exampleSubmissions, attachments, plagiarismCases).filter(Objects::nonNull)
                .forEach(Collection::clear);
    }
}
