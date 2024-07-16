package de.tum.in.www1.artemis.domain;

import java.util.Objects;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;
import de.tum.in.www1.artemis.service.connectors.vcs.AbstractVersionControlService;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Entity
@Table(name = "programming_exercise_build_config")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseBuildConfig extends DomainObject {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseBuildConfig.class);

    @Column(name = "sequential_test_runs")
    private Boolean sequentialTestRuns;

    @Column(name = "branch")
    private String branch;

    @Column(name = "build_plan_configuration", columnDefinition = "longtext")
    private String buildPlanConfiguration;

    @Column(name = "build_script", columnDefinition = "longtext")
    private String buildScript;

    /**
     * This boolean flag determines whether the solution repository should be checked out during the build (additional to the student's submission).
     * This is currently only supported for HASKELL and OCAML, thus the default value is false.
     */
    @Column(name = "checkout_solution_repository", columnDefinition = "boolean default false")
    private boolean checkoutSolutionRepository;

    @Column(name = "checkout_path")
    private String checkoutPath;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "docker_flags")
    private String dockerFlags;

    @OneToOne(mappedBy = "buildConfig")
    @JsonIgnoreProperties("buildConfig")
    private ProgrammingExercise programmingExercise;

    @Column(name = "static_code_analysis_enabled")
    private Boolean staticCodeAnalysisEnabled;

    @Column(name = "max_static_code_analysis_penalty")
    private Integer maxStaticCodeAnalysisPenalty;

    @Column(name = "testwise_coverage_enabled")
    private boolean testwiseCoverageEnabled;

    @Nullable
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "project_type")
    private ProjectType projectType;

    @JsonProperty("sequentialTestRuns")
    public boolean hasSequentialTestRuns() {
        return Objects.requireNonNullElse(sequentialTestRuns, false);
    }

    public void setSequentialTestRuns(Boolean sequentialTestRuns) {
        this.sequentialTestRuns = sequentialTestRuns;
    }

    /**
     * Getter for the stored default branch of the exercise.
     * Use {@link AbstractVersionControlService#getOrRetrieveBranchOfExercise(ProgrammingExercise)} if you are not sure that the value was already set in the Artemis database
     *
     * @return the name of the default branch or null if not yet stored in Artemis
     */
    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    /**
     * Returns the JSON encoded custom build plan configuration
     *
     * @return the JSON encoded custom build plan configuration or null if the default one should be used
     */
    public String getBuildPlanConfiguration() {
        return buildPlanConfiguration;
    }

    /**
     * Sets the JSON encoded custom build plan configuration
     *
     * @param buildPlanConfiguration the JSON encoded custom build plan configuration
     */
    public void setBuildPlanConfiguration(String buildPlanConfiguration) {
        this.buildPlanConfiguration = buildPlanConfiguration;
    }

    /**
     * We store the bash script in the database
     *
     * @return the build script or null if the build script does not exist
     */
    public String getBuildScript() {
        return buildScript;
    }

    /**
     * Update the build script
     *
     * @param buildScript the new build script for the programming exercise
     */
    public void setBuildScript(String buildScript) {
        this.buildScript = buildScript;
    }

    public Boolean getCheckoutSolutionRepository() {
        return checkoutSolutionRepository;
    }

    public void setCheckoutSolutionRepository(Boolean checkoutSolutionRepository) {
        this.checkoutSolutionRepository = checkoutSolutionRepository;
    }

    public String getCheckoutPath() {
        return checkoutPath;
    }

    public void setCheckoutPath(String checkoutPath) {
        this.checkoutPath = checkoutPath;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getDockerFlags() {
        return dockerFlags;
    }

    public void setDockerFlags(String dockerFlags) {
        this.dockerFlags = dockerFlags;
    }

    /**
     * We store the build plan configuration as a JSON string in the database, as it is easier to handle than a complex object structure.
     * This method parses the JSON string and returns a {@link Windfile} object.
     *
     * @return the {@link Windfile} object or null if the JSON string could not be parsed
     */
    public Windfile getWindfile() {
        if (buildPlanConfiguration == null) {
            return null;
        }
        try {
            return Windfile.deserialize(buildPlanConfiguration);
        }
        catch (JsonProcessingException e) {
            log.error("Could not parse build plan configuration for programming exercise {}", this.getId(), e);
        }
        return null;
    }

    public void filterSensitiveInformation() {
        setBuildPlanConfiguration(null);
        setBuildScript(null);
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

    public ProgrammingExercise getProgrammingExercise() {
        return programmingExercise;
    }

    public void setProgrammingExercise(ProgrammingExercise programmingExercise) {
        this.programmingExercise = programmingExercise;
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

    @Override
    public String toString() {
        return "BuildJobConfig{" + "id=" + getId() + ", sequentialTestRuns=" + sequentialTestRuns + ", branch='" + branch + '\'' + ", buildPlanConfiguration='"
                + buildPlanConfiguration + '\'' + ", buildScript='" + buildScript + '\'' + ", checkoutSolutionRepository=" + checkoutSolutionRepository + ", checkoutPath='"
                + checkoutPath + '\'' + ", timeoutSeconds=" + timeoutSeconds + ", dockerFlags='" + dockerFlags + '\'' + '}';
    }
}
