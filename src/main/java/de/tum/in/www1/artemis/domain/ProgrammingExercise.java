package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.domain.enumeration.ExerciseType.PROGRAMMING;

import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSettings;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.service.ExerciseDateService;
import de.tum.in.www1.artemis.service.connectors.vcs.AbstractVersionControlService;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * A ProgrammingExercise.
 */
@Entity
@DiscriminatorValue(value = "P")
@SecondaryTable(name = "programming_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExercise extends Exercise {

    // used to distinguish the type when used in collections (e.g. SearchResultPageDTO --> resultsOnPage)
    public String getType() {
        return "programming";
    }

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExercise.class);

    @Column(name = "test_repository_url")
    private String testRepositoryUrl;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties(value = "exercise", allowSetters = true)
    @OrderColumn(name = "programming_exercise_auxiliary_repositories_order")
    private List<AuxiliaryRepository> auxiliaryRepositories = new ArrayList<>();

    @Column(name = "publish_build_plan_url")
    private Boolean publishBuildPlanUrl;

    @Column(name = "allow_online_editor", table = "programming_exercise_details")
    private Boolean allowOnlineEditor;

    @Column(name = "allow_offline_ide", table = "programming_exercise_details")
    private Boolean allowOfflineIde;

    @Column(name = "static_code_analysis_enabled", table = "programming_exercise_details")
    private Boolean staticCodeAnalysisEnabled;

    @Column(name = "max_static_code_analysis_penalty", table = "programming_exercise_details")
    private Integer maxStaticCodeAnalysisPenalty;

    @Enumerated(EnumType.STRING)
    @Column(name = "programming_language")
    private ProgrammingLanguage programmingLanguage;

    @Column(name = "package_name")
    private String packageName;

    @Column(name = "sequential_test_runs")
    private Boolean sequentialTestRuns;

    @Column(name = "show_test_names_to_students", table = "programming_exercise_details")
    private boolean showTestNamesToStudents;

    @Nullable
    @Column(name = "build_and_test_student_submissions_after_due_date", table = "programming_exercise_details")
    private ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate;

    @Nullable
    @Column(name = "test_cases_changed", table = "programming_exercise_details")
    private Boolean testCasesChanged = false;   // default value

    @Column(name = "project_key", table = "programming_exercise_details", nullable = false)
    private String projectKey;

    @Size(max = 36)
    @Nullable
    @Column(name = "build_plan_access_secret", table = "programming_exercise_details", length = 36)
    private String buildPlanAccessSecret;

    @OneToOne(cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(unique = true, name = "template_participation_id")
    @JsonIgnoreProperties("programmingExercise")
    private TemplateProgrammingExerciseParticipation templateParticipation;

    @OneToOne(cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(unique = true, name = "solution_participation_id")
    @JsonIgnoreProperties("programmingExercise")
    private SolutionProgrammingExerciseParticipation solutionParticipation;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("exercise")
    private Set<ProgrammingExerciseTestCase> testCases = new HashSet<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("exercise")
    private List<ProgrammingExerciseTask> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("exercise")
    private Set<StaticCodeAnalysisCategory> staticCodeAnalysisCategories = new HashSet<>();

    @OneToOne(cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(unique = true, name = "submission_policy_id")
    @JsonIgnoreProperties("programmingExercise")
    private SubmissionPolicy submissionPolicy;

    @Nullable
    @Column(name = "project_type", table = "programming_exercise_details")
    private ProjectType projectType;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ExerciseHint> exerciseHints = new HashSet<>();

    @Column(name = "testwise_coverage_enabled", table = "programming_exercise_details")
    private boolean testwiseCoverageEnabled;

    @Column(name = "branch", table = "programming_exercise_details")
    private String branch;

    @Column(name = "release_tests_with_example_solution", table = "programming_exercise_details")
    private boolean releaseTestsWithExampleSolution;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "iris_settings_id", table = "programming_exercise_details")
    private IrisSettings irisSettings;

    /**
     * This boolean flag determines whether the solution repository should be checked out during the build (additional to the student's submission).
     * This property is only used when creating the exercise (the client sets this value when POSTing the new exercise to the server).
     * It is not persisted as this setting can not be changed afterwards.
     * This is currently only supported for HASKELL and OCAML on BAMBOO, thus the default value is false.
     */
    @Transient
    private boolean checkoutSolutionRepositoryTransient = false;

    /**
     * Convenience getter. The actual URL is stored in the {@link TemplateProgrammingExerciseParticipation}
     *
     * @return The URL of the template repository as a String
     */
    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove
    @JsonIgnore
    public String getTemplateRepositoryUrl() {
        if (templateParticipation != null && Hibernate.isInitialized(templateParticipation)) {
            return templateParticipation.getRepositoryUrl();
        }
        return null;
    }

    public void setTemplateRepositoryUrl(String templateRepositoryUrl) {
        if (templateParticipation != null && Hibernate.isInitialized(templateParticipation)) {
            this.templateParticipation.setRepositoryUrl(templateRepositoryUrl);
        }
    }

    /**
     * Convenience getter. The actual URL is stored in the {@link SolutionProgrammingExerciseParticipation}
     *
     * @return The URL of the solution repository as a String
     */
    @JsonIgnore
    public String getSolutionRepositoryUrl() {
        if (solutionParticipation != null && Hibernate.isInitialized(solutionParticipation)) {
            return solutionParticipation.getRepositoryUrl();
        }
        return null;
    }

    public void setSolutionRepositoryUrl(String solutionRepositoryUrl) {
        if (solutionParticipation != null && Hibernate.isInitialized(solutionParticipation)) {
            this.solutionParticipation.setRepositoryUrl(solutionRepositoryUrl);
        }
    }

    public void setTestRepositoryUrl(String testRepositoryUrl) {
        this.testRepositoryUrl = testRepositoryUrl;
    }

    public String getTestRepositoryUrl() {
        return testRepositoryUrl;
    }

    public List<AuxiliaryRepository> getAuxiliaryRepositories() {
        return this.auxiliaryRepositories;
    }

    public void setAuxiliaryRepositories(List<AuxiliaryRepository> auxiliaryRepositories) {
        this.auxiliaryRepositories = auxiliaryRepositories;
    }

    /**
     * @return an unmodifiable list of auxiliary repositories used in the build plan
     */
    @JsonIgnore
    public List<AuxiliaryRepository> getAuxiliaryRepositoriesForBuildPlan() {
        return this.auxiliaryRepositories.stream().filter(AuxiliaryRepository::shouldBeIncludedInBuildPlan).toList();
    }

    public void addAuxiliaryRepository(AuxiliaryRepository repository) {
        this.getAuxiliaryRepositories().add(repository);
        repository.setExercise(this);
    }

    @JsonIgnore // we now store it in templateParticipation --> this is just a convenience getter
    public String getTemplateBuildPlanId() {
        if (templateParticipation != null && Hibernate.isInitialized(templateParticipation)) {
            return templateParticipation.getBuildPlanId();
        }
        return null;
    }

    private void setTemplateBuildPlanId(String templateBuildPlanId) {
        if (templateParticipation != null && Hibernate.isInitialized(templateParticipation)) {
            this.templateParticipation.setBuildPlanId(templateBuildPlanId);
        }
    }

    @JsonIgnore // we now store it in solutionParticipation --> this is just a convenience getter
    public String getSolutionBuildPlanId() {
        if (solutionParticipation != null && Hibernate.isInitialized(solutionParticipation)) {
            return solutionParticipation.getBuildPlanId();
        }
        return null;
    }

    private void setSolutionBuildPlanId(String solutionBuildPlanId) {
        if (solutionParticipation != null && Hibernate.isInitialized(solutionParticipation)) {
            this.solutionParticipation.setBuildPlanId(solutionBuildPlanId);
        }
    }

    public Boolean isPublishBuildPlanUrl() {
        return publishBuildPlanUrl;
    }

    public void setPublishBuildPlanUrl(Boolean publishBuildPlanUrl) {
        this.publishBuildPlanUrl = publishBuildPlanUrl;
    }

    public Boolean isAllowOnlineEditor() {
        return allowOnlineEditor;
    }

    public void setAllowOnlineEditor(Boolean allowOnlineEditor) {
        this.allowOnlineEditor = allowOnlineEditor;
    }

    public Boolean isAllowOfflineIde() {
        return allowOfflineIde;
    }

    public void setAllowOfflineIde(Boolean allowOfflineIde) {
        this.allowOfflineIde = allowOfflineIde;
    }

    public Boolean isStaticCodeAnalysisEnabled() {
        return this.staticCodeAnalysisEnabled;
    }

    public void setStaticCodeAnalysisEnabled(Boolean staticCodeAnalysisEnabled) {
        this.staticCodeAnalysisEnabled = staticCodeAnalysisEnabled;
    }

    public Integer getMaxStaticCodeAnalysisPenalty() {
        return maxStaticCodeAnalysisPenalty;
    }

    public void setMaxStaticCodeAnalysisPenalty(Integer maxStaticCodeAnalysisPenalty) {
        this.maxStaticCodeAnalysisPenalty = maxStaticCodeAnalysisPenalty;
    }

    public String getProjectKey() {
        return this.projectKey;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    /**
     * Getter for the stored default branch of the exercise.
     * Use {@link AbstractVersionControlService#getOrRetrieveBranchOfExercise(ProgrammingExercise)} if you are not sure that the value was already set in the Artemis database
     *
     * @return the name of the default branch or null if not yet stored in Artemis
     */
    @JsonIgnore
    public String getBranch() {
        return branch;
    }

    public void setReleaseTestsWithExampleSolution(boolean releaseTestsWithExampleSolution) {
        this.releaseTestsWithExampleSolution = releaseTestsWithExampleSolution;
    }

    public boolean isReleaseTestsWithExampleSolution() {
        return releaseTestsWithExampleSolution;
    }

    /**
     * Generates the full repository name for a given repository type.
     *
     * @param repositoryType The repository type
     * @return The repository name
     */
    public String generateRepositoryName(RepositoryType repositoryType) {
        return generateRepositoryName(repositoryType.getName());
    }

    /**
     * Generates the full repository name for a given repository name.
     *
     * @param repositoryName The simple name of the repository
     * @return The full name of the repository
     */
    public String generateRepositoryName(String repositoryName) {
        generateAndSetProjectKey();
        return this.projectKey.toLowerCase() + "-" + repositoryName;
    }

    /**
     * Generates the build plan id for a given build plan type.
     *
     * @param buildPlanType The build plan type
     * @return The build plan id
     */
    public String generateBuildPlanId(BuildPlanType buildPlanType) {
        generateAndSetProjectKey();
        return this.projectKey + "-" + buildPlanType.getName();
    }

    /**
     * Generates a unique project key based on the course short name and the exercise short name. This should only be used
     * for instantiating a new exercise
     *
     * The key concatenates the course short name and the exercise short name (in upper case letters), e.g.: <br>
     * Course: <code>crs</code> <br>
     * Exercise: <code>exc</code> <br>
     * Project key: <code>CRSEXC</code>
     */
    public void generateAndSetProjectKey() {
        // Don't set the project key, if it has already been set
        if (this.projectKey != null) {
            return;
        }
        // Get course over exerciseGroup for exam programming exercises
        forceNewProjectKey();
    }

    public void forceNewProjectKey() {
        Course course = getCourseViaExerciseGroupOrCourseMember();
        this.projectKey = (course.getShortName() + this.getShortName()).toUpperCase().replaceAll("\\s+", "");
    }

    /**
     * Get the latest (potentially) graded submission for a programming exercise.
     * Programming submissions work differently in this regard as a submission without a result does not mean it is not rated/assessed,
     * but that e.g. the CI system failed to deliver the build results.
     *
     * @param submissions Submissions for the given student.
     * @return the latest graded submission.
     */
    @Nullable
    @Override
    public Submission findAppropriateSubmissionByResults(Set<Submission> submissions) {
        return submissions.stream().filter(submission -> {
            Result result = submission.getLatestResult();
            if (result != null) {
                return checkForRatedAndAssessedResult(result);
            }
            return this.getDueDate() == null || SubmissionType.INSTRUCTOR.equals(submission.getType()) || SubmissionType.TEST.equals(submission.getType())
                    || submission.getSubmissionDate().isBefore(getRelevantDueDateForSubmission(submission));
        }).max(Comparator.naturalOrder()).orElse(null);
    }

    private ZonedDateTime getRelevantDueDateForSubmission(Submission submission) {
        if (submission.getParticipation().getIndividualDueDate() != null) {
            return submission.getParticipation().getIndividualDueDate();
        }
        else {
            return this.getDueDate();
        }
    }

    @Override
    public ExerciseType getExerciseType() {
        return PROGRAMMING;
    }

    public ProgrammingLanguage getProgrammingLanguage() {
        return programmingLanguage;
    }

    public ProgrammingExercise programmingLanguage(ProgrammingLanguage programmingLanguage) {
        this.programmingLanguage = programmingLanguage;
        return this;
    }

    public void setProgrammingLanguage(ProgrammingLanguage programmingLanguage) {
        this.programmingLanguage = programmingLanguage;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public TemplateProgrammingExerciseParticipation getTemplateParticipation() {
        return templateParticipation;
    }

    public void setTemplateParticipation(TemplateProgrammingExerciseParticipation templateParticipation) {
        this.templateParticipation = templateParticipation;
        if (this.templateParticipation != null) {
            this.templateParticipation.setProgrammingExercise(this);
        }
    }

    public SolutionProgrammingExerciseParticipation getSolutionParticipation() {
        return solutionParticipation;
    }

    public void setSolutionParticipation(SolutionProgrammingExerciseParticipation solutionParticipation) {
        this.solutionParticipation = solutionParticipation;
        if (this.solutionParticipation != null) {
            this.solutionParticipation.setProgrammingExercise(this);
        }
    }

    public SubmissionPolicy getSubmissionPolicy() {
        return this.submissionPolicy;
    }

    public void setSubmissionPolicy(SubmissionPolicy submissionPolicy) {
        this.submissionPolicy = submissionPolicy;
    }

    // jhipster-needle-entity-add-getters-setters - Jhipster will add getters and setters here, do not remove

    /**
     * Gets a URL of the templateRepositoryUrl if there is one
     *
     * @return a URL object of the templateRepositoryUrl or null if there is no templateRepositoryUrl
     */
    @JsonIgnore
    public VcsRepositoryUrl getVcsTemplateRepositoryUrl() {
        var templateRepositoryUrl = getTemplateRepositoryUrl();
        if (templateRepositoryUrl == null || templateRepositoryUrl.isEmpty()) {
            return null;
        }

        try {
            return new VcsRepositoryUrl(templateRepositoryUrl);
        }
        catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Gets a URL of the solutionRepositoryUrl if there is one
     *
     * @return a URL object of the solutionRepositoryUrl or null if there is no solutionRepositoryUrl
     */
    @JsonIgnore
    public VcsRepositoryUrl getVcsSolutionRepositoryUrl() {
        var solutionRepositoryUrl = getSolutionRepositoryUrl();
        if (solutionRepositoryUrl == null || solutionRepositoryUrl.isEmpty()) {
            return null;
        }

        try {
            return new VcsRepositoryUrl(solutionRepositoryUrl);
        }
        catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Gets a URL of the testRepositoryURL if there is one
     *
     * @return a URL object of the testRepositoryURl or null if there is no testRepositoryUrl
     */
    @JsonIgnore
    public VcsRepositoryUrl getVcsTestRepositoryUrl() {
        if (testRepositoryUrl == null || testRepositoryUrl.isEmpty()) {
            return null;
        }

        try {
            return new VcsRepositoryUrl(testRepositoryUrl);
        }
        catch (URISyntaxException e) {
            log.warn("Cannot create URI for testRepositoryUrl: {} due to the following error: {}", testRepositoryUrl, e.getMessage());
        }
        return null;
    }

    /**
     * Returns the repository url for the given repository type.
     *
     * @param repositoryType The repository type for which the url should be returned
     * @return The repository url
     */
    @JsonIgnore
    public VcsRepositoryUrl getRepositoryURL(RepositoryType repositoryType) {
        return switch (repositoryType) {
            case TEMPLATE -> this.getVcsTemplateRepositoryUrl();
            case SOLUTION -> this.getVcsSolutionRepositoryUrl();
            case TESTS -> this.getVcsTestRepositoryUrl();
            default -> throw new UnsupportedOperationException("Can retrieve URL for repository type " + repositoryType);
        };
    }

    /**
     * Returns the project name by concatenating the course short name with the exercise title.
     *
     * @return project name of the programming exercise
     */
    @JsonIgnore
    public String getProjectName() {
        // this is the name used for VC service and CI service
        return getCourseViaExerciseGroupOrCourseMember().getShortName() + " " + this.getTitle();
    }

    @JsonIgnore
    public String getPackageFolderName() {
        return getPackageName().replace(".", "/");
    }

    public Set<ProgrammingExerciseTestCase> getTestCases() {
        return testCases;
    }

    public void setTestCases(Set<ProgrammingExerciseTestCase> testCases) {
        this.testCases = testCases;
    }

    public List<ProgrammingExerciseTask> getTasks() {
        return tasks;
    }

    public void setTasks(List<ProgrammingExerciseTask> tasks) {
        this.tasks = tasks;
    }

    public Set<StaticCodeAnalysisCategory> getStaticCodeAnalysisCategories() {
        return staticCodeAnalysisCategories;
    }

    public void setStaticCodeAnalysisCategories(Set<StaticCodeAnalysisCategory> staticCodeAnalysisCategories) {
        this.staticCodeAnalysisCategories = staticCodeAnalysisCategories;
    }

    public void addStaticCodeAnalysisCategory(final StaticCodeAnalysisCategory category) {
        staticCodeAnalysisCategories.add(category);
    }

    @JsonProperty("sequentialTestRuns")
    public boolean hasSequentialTestRuns() {
        return Objects.requireNonNullElse(sequentialTestRuns, false);
    }

    public void setSequentialTestRuns(Boolean sequentialTestRuns) {
        this.sequentialTestRuns = sequentialTestRuns;
    }

    public Boolean getShowTestNamesToStudents() {
        return showTestNamesToStudents;
    }

    public void setShowTestNamesToStudents(Boolean showTestNamesToStudents) {
        this.showTestNamesToStudents = showTestNamesToStudents;
    }

    @Nullable
    public ZonedDateTime getBuildAndTestStudentSubmissionsAfterDueDate() {
        return buildAndTestStudentSubmissionsAfterDueDate;
    }

    public void setBuildAndTestStudentSubmissionsAfterDueDate(@Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate) {
        this.buildAndTestStudentSubmissionsAfterDueDate = buildAndTestStudentSubmissionsAfterDueDate;
    }

    public boolean getTestCasesChanged() {
        return Objects.requireNonNullElse(testCasesChanged, false);
    }

    public void setTestCasesChanged(boolean testCasesChanged) {
        this.testCasesChanged = testCasesChanged;
    }

    @Override
    public AssessmentType getAssessmentType() {
        if (super.getAssessmentType() == null) {
            return AssessmentType.AUTOMATIC;
        }
        return super.getAssessmentType();
    }

    @Nullable
    public ProjectType getProjectType() {
        return projectType;
    }

    public void setProjectType(@Nullable ProjectType projectType) {
        this.projectType = projectType;
    }

    public Boolean isTestwiseCoverageEnabled() {
        return testwiseCoverageEnabled;
    }

    public void setTestwiseCoverageEnabled(Boolean testwiseCoverageEnabled) {
        this.testwiseCoverageEnabled = testwiseCoverageEnabled;
    }

    /**
     * set all sensitive information to null, so no info with respect to the solution gets leaked to students through json
     */
    @Override
    public void filterSensitiveInformation() {
        setTemplateRepositoryUrl(null);
        setSolutionRepositoryUrl(null);
        setTestRepositoryUrl(null);
        setTemplateBuildPlanId(null);
        setSolutionBuildPlanId(null);
        super.filterSensitiveInformation();
    }

    /**
     * Get all results of a student participation which are rated or unrated
     *
     * @param participation The current participation
     * @return all results which are completed and are either automatic or manually assessed
     */
    @Override
    public Set<Result> findResultsFilteredForStudents(Participation participation) {
        return participation.getResults().stream().filter(this::checkForAssessedResult).collect(Collectors.toSet());
    }

    /**
     * Find relevant participations for this exercise. Normally there are only one practice and graded participation.
     * In case there are multiple, they are filtered as implemented in {@link Exercise#findRelevantParticipation(List)}
     *
     * @param participations the list of available participations
     * @return the found participation in an unmodifiable list or the empty list, if none exists
     */
    @Override
    public List<StudentParticipation> findRelevantParticipation(List<StudentParticipation> participations) {
        List<StudentParticipation> participationOfExercise = participations.stream()
                .filter(participation -> participation.getExercise() != null && participation.getExercise().equals(this)).toList();
        List<StudentParticipation> gradedParticipations = participationOfExercise.stream().filter(participation -> !participation.isPracticeMode()).toList();
        List<StudentParticipation> practiceParticipations = participationOfExercise.stream().filter(Participation::isPracticeMode).toList();

        if (gradedParticipations.size() > 1) {
            gradedParticipations = super.findRelevantParticipation(gradedParticipations);
        }
        if (practiceParticipations.size() > 1) {
            practiceParticipations = super.findRelevantParticipation(practiceParticipations);
        }

        return Stream.concat(gradedParticipations.stream(), practiceParticipations.stream()).toList();
    }

    /**
     * Check if manual results are allowed for the exercise
     *
     * @return true if manual results are allowed, false otherwise
     */
    public boolean areManualResultsAllowed() {
        // Only allow manual results for programming exercises if option was enabled and due dates have passed;
        if (getAssessmentType() == AssessmentType.SEMI_AUTOMATIC || getAllowComplaintsForAutomaticAssessments()) {
            // The relevantDueDate check below keeps us from assessing feedback requests,
            // as their relevantDueDate is before the due date
            if (getAllowManualFeedbackRequests()) {
                return true;
            }

            final var relevantDueDate = getBuildAndTestStudentSubmissionsAfterDueDate() != null ? getBuildAndTestStudentSubmissionsAfterDueDate() : getDueDate();
            return (relevantDueDate == null || relevantDueDate.isBefore(ZonedDateTime.now()));
        }

        return false;
    }

    /**
     * This checks if the current result is rated and has a completion date.
     *
     * @param result The current result
     * @return true if the result is manual and assessed, false otherwise
     */
    private boolean checkForRatedAndAssessedResult(Result result) {
        return Boolean.TRUE.equals(result.isRated()) && checkForAssessedResult(result);
    }

    /**
     * This checks if the current result has a completion date and if the assessment is over
     *
     * @param result The current result
     * @return true if the result is manual and the assessment is over, or it is an automatic result, false otherwise
     */
    private boolean checkForAssessedResult(Result result) {
        return result.getCompletionDate() != null && ((result.isManual() && ExerciseDateService.isAfterAssessmentDueDate(this)) || result.isAutomatic());
    }

    @Override
    public String toString() {
        return "ProgrammingExercise{" + "id=" + getId() + ", templateRepositoryUrl='" + getTemplateRepositoryUrl() + "'" + ", solutionRepositoryUrl='" + getSolutionRepositoryUrl()
                + "'" + ", templateBuildPlanId='" + getTemplateBuildPlanId() + "'" + ", solutionBuildPlanId='" + getSolutionBuildPlanId() + "'" + ", publishBuildPlanUrl='"
                + isPublishBuildPlanUrl() + "'" + ", allowOnlineEditor='" + isAllowOnlineEditor() + "'" + ", programmingLanguage='" + getProgrammingLanguage() + "'"
                + ", packageName='" + getPackageName() + "'" + ", testCasesChanged='" + testCasesChanged + "'" + "}";
    }

    public boolean getCheckoutSolutionRepository() {
        return this.checkoutSolutionRepositoryTransient;
    }

    public void setCheckoutSolutionRepository(boolean checkoutSolutionRepository) {
        this.checkoutSolutionRepositoryTransient = checkoutSolutionRepository;
    }

    /**
     * Validates general programming exercise settings
     * 1. Validates the programming language
     */
    public void validateProgrammingSettings() {

        // Check if a participation mode was selected
        if (!Boolean.TRUE.equals(isAllowOnlineEditor()) && !Boolean.TRUE.equals(isAllowOfflineIde())) {
            throw new BadRequestAlertException("You need to allow at least one participation mode, the online editor or the offline IDE", "Exercise", "noParticipationModeAllowed");
        }

        // Check if Xcode has no online code editor enabled
        if (ProjectType.XCODE.equals(getProjectType()) && Boolean.TRUE.equals(isAllowOnlineEditor())) {
            throw new BadRequestAlertException("The online editor is not allowed for Xcode programming exercises", "Exercise", "noParticipationModeAllowed");
        }

        // Check if programming language is set
        if (getProgrammingLanguage() == null) {
            throw new BadRequestAlertException("No programming language was specified", "Exercise", "programmingLanguageNotSet");
        }
    }

    /**
     * Validates the static code analysis settings of the programming exercise
     * 1. The flag staticCodeAnalysisEnabled must not be null
     * 2. Static code analysis and sequential test runs can't be active at the same time
     * 3. Static code analysis can only be enabled for supported programming languages
     * 4. Static code analysis max penalty must only be set if static code analysis is enabled
     * 5. Static code analysis max penalty must be positive
     *
     * @param programmingLanguageFeature describes the features available for the programming language of the programming exercise
     */
    public void validateStaticCodeAnalysisSettings(ProgrammingLanguageFeature programmingLanguageFeature) {
        // Check if the static code analysis flag was set
        if (isStaticCodeAnalysisEnabled() == null) {
            throw new BadRequestAlertException("The static code analysis flag must be set to true or false", "Exercise", "staticCodeAnalysisFlagNotSet");
        }

        // Check that programming exercise doesn't have sequential test runs and static code analysis enabled
        if (Boolean.TRUE.equals(isStaticCodeAnalysisEnabled()) && hasSequentialTestRuns()) {
            throw new BadRequestAlertException("The static code analysis with sequential test runs is not supported at the moment", "Exercise", "staticCodeAnalysisAndSequential");
        }

        // Check if the programming language supports static code analysis
        if (Boolean.TRUE.equals(isStaticCodeAnalysisEnabled()) && !programmingLanguageFeature.staticCodeAnalysis()) {
            throw new BadRequestAlertException("The static code analysis is not supported for this programming language", "Exercise", "staticCodeAnalysisNotSupportedForLanguage");
        }

        // Check that FACT has no SCA enabled
        if (Boolean.TRUE.equals(isStaticCodeAnalysisEnabled()) && ProjectType.FACT.equals(getProjectType())) {
            throw new BadRequestAlertException("The static code analysis is not supported for FACT programming exercises", "Exercise", "staticCodeAnalysisNotSupportedForLanguage");
        }

        // Static code analysis max penalty must only be set if static code analysis is enabled
        if (Boolean.FALSE.equals(isStaticCodeAnalysisEnabled()) && getMaxStaticCodeAnalysisPenalty() != null) {
            throw new BadRequestAlertException("Max static code analysis penalty must only be set if static code analysis is enabled", "Exercise",
                    "staticCodeAnalysisDisabledButPenaltySet");
        }

        // Static code analysis max penalty must be positive
        if (getMaxStaticCodeAnalysisPenalty() != null && getMaxStaticCodeAnalysisPenalty() < 0) {
            throw new BadRequestAlertException("The static code analysis penalty must not be negative", "Exercise", "staticCodeAnalysisPenaltyNotNegative");
        }
    }

    /**
     * Validates settings for exercises, where allowManualFeedbackRequests is set
     */
    public void validateManualFeedbackSettings() {
        if (!this.getAllowManualFeedbackRequests()) {
            return;
        }

        if (this.getAssessmentType() == AssessmentType.AUTOMATIC) {
            throw new BadRequestAlertException("Assessment type is not manual", "Exercise", "invalidManualFeedbackSettings");
        }

        if (this.getDueDate() == null) {
            throw new BadRequestAlertException("Exercise due date is not set", "Exercise", "invalidManualFeedbackSettings");
        }

        if (this.buildAndTestStudentSubmissionsAfterDueDate != null) {
            throw new BadRequestAlertException("Cannot run tests after due date", "Exercise", "invalidManualFeedbackSettings");
        }
    }

    public Set<ExerciseHint> getExerciseHints() {
        return exerciseHints;
    }

    public void setExerciseHints(Set<ExerciseHint> exerciseHints) {
        this.exerciseHints = exerciseHints;
    }

    public boolean hasBuildPlanAccessSecretSet() {
        return buildPlanAccessSecret != null && !buildPlanAccessSecret.isEmpty();
    }

    @Nullable
    public String getBuildPlanAccessSecret() {
        return buildPlanAccessSecret;
    }

    public void generateAndSetBuildPlanAccessSecret() {
        buildPlanAccessSecret = UUID.randomUUID().toString();
    }

    public IrisSettings getIrisSettings() {
        return irisSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnectRelatedEntities() {
        Stream.of(exerciseHints, testCases, staticCodeAnalysisCategories).filter(Objects::nonNull).forEach(Collection::clear);

        super.disconnectRelatedEntities();
    }

    public void setIrisSettings(IrisSettings irisSettings) {
        this.irisSettings = irisSettings;
    }
}
