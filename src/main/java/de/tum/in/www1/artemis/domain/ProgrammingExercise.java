package de.tum.in.www1.artemis.domain;

import java.net.MalformedURLException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;

/**
 * A ProgrammingExercise.
 */
@Entity
@DiscriminatorValue(value = "P")
@SecondaryTable(name = "programming_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExercise extends Exercise {

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
    private Set<StaticCodeAnalysisCategory> staticCodeAnalysisCategories = new HashSet<>();

    @Transient
    private boolean isLocalSimulationTransient;

    @Nullable
    @Column(name = "project_type", table = "programming_exercise_details")
    private ProjectType projectType;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_policy_type", table = "programming_exercise_details")
    private SubmissionPolicyType submissionPolicyType;

    /**
     * If submissionPolicyType is not NONE, this attribute represents the maximum
     * number of submissions until the submission policy takes effect.
     */
    @Column(name = "max_number_of_submissions", table = "programming_exercise_details")
    private Integer maxNumberOfSubmissions;

    /**
     * This boolean flag determines whether the solution repository should be checked out during the build (additional to the student's submission).
     * This property is only used when creating the exercise (the client sets this value when POSTing the new exercise to the server).
     * It is not persisted as this setting can not be changed afterwards.
     * This is currently only supported for HASKELL on BAMBOO, thus the default value is false.
     */
    @Transient
    @JsonProperty
    private boolean checkoutSolutionRepository = false;

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

    @JsonIgnore
    public String getTemplateRepositoryName() {
        return getRepositoryNameFor(getTemplateRepositoryUrl(), RepositoryType.TEMPLATE);
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

    private void setSolutionRepositoryUrl(String solutionRepositoryUrl) {
        if (solutionParticipation != null && Hibernate.isInitialized(solutionParticipation)) {
            this.solutionParticipation.setRepositoryUrl(solutionRepositoryUrl);
        }
    }

    @JsonIgnore
    public String getSolutionRepositoryName() {
        return getRepositoryNameFor(getSolutionRepositoryUrl(), RepositoryType.SOLUTION);
    }

    public void setTestRepositoryUrl(String testRepositoryUrl) {
        this.testRepositoryUrl = testRepositoryUrl;
    }

    public String getTestRepositoryUrl() {
        return testRepositoryUrl;
    }

    /**
     * Returns the test repository name of the exercise. Test test repository name is extracted from the test repository url.
     *
     * @return the test repository name if a valid test repository url is set. Otherwise returns null!
     */
    public String getTestRepositoryName() {
        return getRepositoryNameFor(getTestRepositoryUrl(), RepositoryType.TESTS);
    }

    /**
     * Get the repository name for any stored repository, i.e. the slug of the repository.
     *
     * @param repoUrl The full URL of the repository
     * @param repoType The repository type, meaning one of the base repositories (template, solution, test)
     * @return The full repository slug for the given URL
     */
    private String getRepositoryNameFor(final String repoUrl, final RepositoryType repoType) {
        if (repoUrl == null) {
            return null;
        }

        Pattern pattern = Pattern.compile(".*/(.*-" + repoType.getName() + ")\\.git");
        Matcher matcher = pattern.matcher(repoUrl);
        if (!matcher.matches() || matcher.groupCount() != 1)
            return null;

        return matcher.group(1);
    }

    public List<AuxiliaryRepository> getAuxiliaryRepositories() {
        return this.auxiliaryRepositories;
    }

    public void setAuxiliaryRepositories(List<AuxiliaryRepository> auxiliaryRepositories) {
        this.auxiliaryRepositories = auxiliaryRepositories;
    }

    @JsonIgnore
    public List<AuxiliaryRepository> getAuxiliaryRepositoriesForBuildPlan() {
        return this.auxiliaryRepositories.stream().filter(AuxiliaryRepository::shouldBeIncludedInBuildPlan).collect(Collectors.toList());
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
        Course course = getCourseViaExerciseGroupOrCourseMember();
        this.projectKey = (course.getShortName() + this.getShortName()).toUpperCase().replaceAll("\\s+", "");
    }

    /**
     * Get the latest (potentially) graded submission for a programming exercise.
     * Programming submissions work differently in this regard as a submission without a result does not mean it is not rated/assessed, but that e.g. the CI system failed to deliver the build results.
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
            return this.getDueDate() == null || submission.getType().equals(SubmissionType.INSTRUCTOR) || submission.getType().equals(SubmissionType.TEST)
                    || submission.getSubmissionDate().isBefore(this.getDueDate());
        }).max(Comparator.comparing(Submission::getSubmissionDate)).orElse(null);
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

    // jhipster-needle-entity-add-getters-setters - Jhipster will add getters and setters here, do not remove

    /**
     * Gets a URL of the  templateRepositoryUrl if there is one
     *
     * @return a URL object of the  templateRepositoryUrl or null if there is no templateRepositoryUrl
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
        catch (MalformedURLException e) {
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
        catch (MalformedURLException e) {
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
        catch (MalformedURLException e) {
            log.warn("Cannot create URL for testRepositoryUrl: {} due to the following error: {}", testRepositoryUrl, e.getMessage());
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
            default -> throw new UnsupportedOperationException("Can retrieve URL for repositorytype " + repositoryType);
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

    public Set<StaticCodeAnalysisCategory> getStaticCodeAnalysisCategories() {
        return staticCodeAnalysisCategories;
    }

    public void setStaticCodeAnalysisCategories(Set<StaticCodeAnalysisCategory> staticCodeAnalysisCategories) {
        this.staticCodeAnalysisCategories = staticCodeAnalysisCategories;
    }

    @JsonProperty("sequentialTestRuns")
    public boolean hasSequentialTestRuns() {
        if (sequentialTestRuns == null) {
            return false;
        }
        return sequentialTestRuns;
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
        if (testCasesChanged == null) {
            return false;
        }
        return testCasesChanged;
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

    public boolean needsLockOperation() {
        return isExamExercise() || !AssessmentType.AUTOMATIC.equals(getAssessmentType()) || getBuildAndTestStudentSubmissionsAfterDueDate() != null;
    }

    @Nullable
    public ProjectType getProjectType() {
        return projectType;
    }

    public void setProjectType(@Nullable ProjectType projectType) {
        this.projectType = projectType;
    }

    public SubmissionPolicyType getSubmissionPolicyType() {
        return this.submissionPolicyType;
    }

    public void setSubmissionPolicyType(SubmissionPolicyType submissionPolicyType) {
        this.submissionPolicyType = submissionPolicyType;
    }

    public Integer getMaxNumberOfSubmissions() {
        return this.maxNumberOfSubmissions;
    }

    public void setMaxNumberOfSubmissions(Integer maxNumberOfSubmissions) {
        this.maxNumberOfSubmissions = maxNumberOfSubmissions;
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
     * @param participation The current participation
     * @return all results which are completed and are either automatic or manually assessed
     */
    @Override
    public Set<Result> findResultsFilteredForStudents(Participation participation) {
        return participation.getResults().stream().filter(result -> checkForAssessedResult(result)).collect(Collectors.toSet());
    }

    /**
     * Check if manual results are allowed for the exercise
     * @return true if manual results are allowed, false otherwise
     */
    public boolean areManualResultsAllowed() {
        // Only allow manual results for programming exercises if option was enabled and due dates have passed;
        final var relevantDueDate = getBuildAndTestStudentSubmissionsAfterDueDate() != null ? getBuildAndTestStudentSubmissionsAfterDueDate() : getDueDate();
        return getAssessmentType() == AssessmentType.SEMI_AUTOMATIC && (relevantDueDate == null || relevantDueDate.isBefore(ZonedDateTime.now()));
    }

    /**
     * This checks if the current result is rated and has a completion date.
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
     * @return true if the result is manual and the assessment is over or it is an automatic result, false otherwise
     */
    private boolean checkForAssessedResult(Result result) {
        boolean isAssessmentOver = getAssessmentDueDate() == null || getAssessmentDueDate().isBefore(ZonedDateTime.now());
        return result.getCompletionDate() != null && ((result.isManual() && isAssessmentOver) || result.getAssessmentType().equals(AssessmentType.AUTOMATIC));
    }

    @Override
    public String toString() {
        return "ProgrammingExercise{" + "id=" + getId() + ", templateRepositoryUrl='" + getTemplateRepositoryUrl() + "'" + ", solutionRepositoryUrl='" + getSolutionRepositoryUrl()
                + "'" + ", templateBuildPlanId='" + getTemplateBuildPlanId() + "'" + ", solutionBuildPlanId='" + getSolutionBuildPlanId() + "'" + ", publishBuildPlanUrl='"
                + isPublishBuildPlanUrl() + "'" + ", allowOnlineEditor='" + isAllowOnlineEditor() + "'" + ", programmingLanguage='" + getProgrammingLanguage() + "'"
                + ", packageName='" + getPackageName() + "'" + ", testCasesChanged='" + testCasesChanged + "'" + "}";
    }

    public boolean getIsLocalSimulation() {
        return this.isLocalSimulationTransient;
    }

    public void setIsLocalSimulation(Boolean isLocalSimulationTransient) {
        this.isLocalSimulationTransient = isLocalSimulationTransient;
    }

    public boolean getCheckoutSolutionRepository() {
        return this.checkoutSolutionRepository;
    }

    public void setCheckoutSolutionRepository(boolean checkoutSolutionRepository) {
        this.checkoutSolutionRepository = checkoutSolutionRepository;
    }

    /**
     * Sets the transient attribute "isLocalSimulation" if the exercises is a programming exercise
     * and the testRepositoryUrl contains the String "artemislocalhost" which is the indicator that the programming exercise has
     * no connection to a version control and continuous integration server
     *
     */
    public void checksAndSetsIfProgrammingExerciseIsLocalSimulation() {
        if (getTestRepositoryUrl().contains("artemislocalhost")) {
            setIsLocalSimulation(true);
        }
    }
}
