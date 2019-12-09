package de.tum.in.www1.artemis.domain;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;

/**
 * A ProgrammingExercise.
 */
@Entity
@DiscriminatorValue(value = "P")
@SecondaryTable(name = "programming_exercise_details")
public class ProgrammingExercise extends Exercise {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExercise.class);

    private static final long serialVersionUID = 1L;

    @Column(name = "test_repository_url")
    private String testRepositoryUrl;

    @Column(name = "publish_build_plan_url")
    private Boolean publishBuildPlanUrl;

    @Column(name = "allow_online_editor")
    private Boolean allowOnlineEditor;

    @Enumerated(EnumType.STRING)
    @Column(name = "programming_language")
    private ProgrammingLanguage programmingLanguage;

    @Column(name = "package_name")
    private String packageName;

    @Column(name = "sequential_test_runs")
    private Boolean sequentialTestRuns;

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

    private void setTemplateRepositoryUrl(String templateRepositoryUrl) {
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

        Pattern p = Pattern.compile(".*/(.*-" + repoType.getName() + ")\\.git");
        Matcher m = p.matcher(repoUrl);
        if (!m.matches() || m.groupCount() != 1)
            return null;

        return m.group(1);
    }

    public ProgrammingExercise testRepositoryUrl(String testRepositoryUrl) {
        this.testRepositoryUrl = testRepositoryUrl;
        return this;
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

    public ProgrammingExercise publishBuildPlanUrl(Boolean publishBuildPlanUrl) {
        this.publishBuildPlanUrl = publishBuildPlanUrl;
        return this;
    }

    public void setPublishBuildPlanUrl(Boolean publishBuildPlanUrl) {
        this.publishBuildPlanUrl = publishBuildPlanUrl;
    }

    public Boolean isAllowOnlineEditor() {
        return allowOnlineEditor;
    }

    public ProgrammingExercise allowOnlineEditor(Boolean allowOnlineEditor) {
        this.allowOnlineEditor = allowOnlineEditor;
        return this;
    }

    public void setAllowOnlineEditor(Boolean allowOnlineEditor) {
        this.allowOnlineEditor = allowOnlineEditor;
    }

    public String getProjectKey() {
        return this.projectKey;
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
        this.projectKey = (this.getCourse().getShortName() + this.getShortName()).toUpperCase().replaceAll("\\s+", "");
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
    protected Submission findAppropriateSubmissionByResults(Set<Submission> submissions) {
        return submissions.stream().filter(submission -> {
            if (submission.getResult() != null) {
                return submission.getResult().isRated();
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

    public ProgrammingExercise packageName(String packageName) {
        this.packageName = packageName;
        return this;
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
    public URL getTemplateRepositoryUrlAsUrl() {
        String templateRepositoryUrl = getTemplateRepositoryUrl();
        if (templateRepositoryUrl == null || templateRepositoryUrl.isEmpty()) {
            return null;
        }
        try {
            return new URL(templateRepositoryUrl);
        }
        catch (MalformedURLException e) {
            log.warn("Cannot create URL for templateRepositoryUrl: " + templateRepositoryUrl + " due to the following error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Gets a URL of the solutionRepositoryUrl if there is one
     *
     * @return a URL object of the solutionRepositoryUrl or null if there is no solutionRepositoryUrl
     */
    @JsonIgnore
    public URL getSolutionRepositoryUrlAsUrl() {
        String solutionRepositoryUrl = getSolutionRepositoryUrl();
        if (solutionRepositoryUrl == null || solutionRepositoryUrl.isEmpty()) {
            return null;
        }
        try {
            return new URL(solutionRepositoryUrl);
        }
        catch (MalformedURLException e) {
            log.warn("Cannot create URL for solutionRepositoryUrl: " + solutionRepositoryUrl + " due to the following error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Gets a URL of the testRepositoryURL if there is one
     *
     * @return a URL object of the testRepositoryURl or null if there is no testRepositoryUrl
     */
    @JsonIgnore
    public URL getTestRepositoryUrlAsUrl() {
        if (testRepositoryUrl == null || testRepositoryUrl.isEmpty()) {
            return null;
        }
        try {
            return new URL(testRepositoryUrl);
        }
        catch (MalformedURLException e) {
            log.warn("Cannot create URL for testRepositoryUrl: " + testRepositoryUrl + " due to the following error: " + e.getMessage());
        }
        return null;
    }

    @JsonIgnore
    public String getProjectName() {
        // this is the name used for Bitbucket and Bamboo
        return this.getCourse().getShortName() + " " + this.getTitle();
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

    @JsonProperty("sequentialTestRuns")
    public Boolean hasSequentialTestRuns() {
        if (sequentialTestRuns == null) {
            return false;
        }
        return sequentialTestRuns;
    }

    public void setSequentialTestRuns(Boolean sequentialTestRuns) {
        this.sequentialTestRuns = sequentialTestRuns;
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
     * Check if manual results are allowed for the exercise
     * @return true if manual results are allowed, false otherwise
     */
    public boolean areManualResultsAllowed() {
        // Only allow manual results for programming exercises if option was enabled and due dates have passed;
        final var relevantDueDate = getBuildAndTestStudentSubmissionsAfterDueDate() != null ? getBuildAndTestStudentSubmissionsAfterDueDate() : getDueDate();
        return getAssessmentType() == AssessmentType.SEMI_AUTOMATIC && (relevantDueDate == null || relevantDueDate.isBefore(ZonedDateTime.now()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProgrammingExercise programmingExercise = (ProgrammingExercise) o;
        if (programmingExercise.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), programmingExercise.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ProgrammingExercise{" + "id=" + getId() + ", templateRepositoryUrl='" + getTemplateRepositoryUrl() + "'" + ", solutionRepositoryUrl='" + getSolutionRepositoryUrl()
                + "'" + ", templateBuildPlanId='" + getTemplateBuildPlanId() + "'" + ", solutionBuildPlanId='" + getSolutionBuildPlanId() + "'" + ", publishBuildPlanUrl='"
                + isPublishBuildPlanUrl() + "'" + ", allowOnlineEditor='" + isAllowOnlineEditor() + "'" + ", programmingLanguage='" + getProgrammingLanguage() + "'"
                + ", packageName='" + getPackageName() + "'" + ", testCasesChanged='" + testCasesChanged + "'" + "}";
    }

    /**
     * Columns for which we allow a pageable search using the {@link ProgrammingExerciseService#getAllOnPageWithSize(PageableSearchDTO, User)} (PageableSearchDTO)}
     * method. This ensures, that we can't search in columns that don't exist, or we do not want to be searchable.
     */
    public enum ProgrammingExerciseSearchColumn {

        ID("id"), TITLE("title"), PROGRAMMING_LANGUAGE("programmingLanguage"), COURSE_TITLE("course.title");

        private String mappedColumnName;

        ProgrammingExerciseSearchColumn(String mappedColumnName) {
            this.mappedColumnName = mappedColumnName;
        }

        public String getMappedColumnName() {
            return mappedColumnName;
        }
    }
}
