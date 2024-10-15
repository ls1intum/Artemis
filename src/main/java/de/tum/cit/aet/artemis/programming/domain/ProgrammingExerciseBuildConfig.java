package de.tum.cit.aet.artemis.programming.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.programming.service.aeolus.Windfile;
import de.tum.cit.aet.artemis.programming.service.vcs.AbstractVersionControlService;

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
    private boolean checkoutSolutionRepository = false;

    @Column(name = "checkout_path")
    private String testCheckoutPath;

    @Column(name = "assignment_checkout_path")
    private String assignmentCheckoutPath;

    @Column(name = "solution_checkout_path")
    private String solutionCheckoutPath;

    @Column(name = "timeout_seconds")
    private int timeoutSeconds;

    @Column(name = "docker_flags")
    private String dockerFlags;

    @OneToOne(mappedBy = "buildConfig")
    @JsonIgnoreProperties("buildConfig")
    private ProgrammingExercise programmingExercise;

    @Column(name = "testwise_coverage_enabled")
    private boolean testwiseCoverageEnabled;

    @Nullable
    @Column(name = "theia_image")
    private String theiaImage;

    @Column(name = "allow_branching", columnDefinition = "boolean default false", nullable = false)
    private boolean allowBranching = false; // default value

    @Column(name = "branch_regex")
    private String branchRegex;

    @Size(max = 36)
    @Nullable
    @Column(name = "build_plan_access_secret", length = 36)
    private String buildPlanAccessSecret;

    public ProgrammingExerciseBuildConfig() {
    }

    public ProgrammingExerciseBuildConfig(ProgrammingExerciseBuildConfig originalBuildConfig) {
        this.setBranch(originalBuildConfig.getBranch());
        this.setBuildPlanConfiguration(originalBuildConfig.getBuildPlanConfiguration());
        this.setTestCheckoutPath(originalBuildConfig.getTestCheckoutPath());
        this.setAssignmentCheckoutPath(originalBuildConfig.getAssignmentCheckoutPath());
        this.setSolutionCheckoutPath(originalBuildConfig.getSolutionCheckoutPath());
        this.setCheckoutSolutionRepository(originalBuildConfig.getCheckoutSolutionRepository());
        this.setDockerFlags(originalBuildConfig.getDockerFlags());
        this.setSequentialTestRuns(originalBuildConfig.hasSequentialTestRuns());
        this.setBuildScript(originalBuildConfig.getBuildScript());
        this.setTestwiseCoverageEnabled(originalBuildConfig.isTestwiseCoverageEnabled());
        this.setTimeoutSeconds(originalBuildConfig.getTimeoutSeconds());
        this.setTheiaImage(originalBuildConfig.getTheiaImage());
        this.setAllowBranching(originalBuildConfig.isAllowBranching());
        this.setBranchRegex(originalBuildConfig.getBranchRegex());
        this.setProgrammingExercise(null);
        this.buildPlanAccessSecret = null;
    }

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

    public boolean getCheckoutSolutionRepository() {
        return checkoutSolutionRepository;
    }

    public void setCheckoutSolutionRepository(boolean checkoutSolutionRepository) {
        this.checkoutSolutionRepository = checkoutSolutionRepository;
    }

    public String getTestCheckoutPath() {
        return testCheckoutPath;
    }

    public void setTestCheckoutPath(String testCheckoutPath) {
        this.testCheckoutPath = testCheckoutPath;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getDockerFlags() {
        return dockerFlags;
    }

    public void setDockerFlags(String dockerFlags) {
        this.dockerFlags = dockerFlags;
    }

    @Nullable
    public String getTheiaImage() {
        return theiaImage;
    }

    public void setTheiaImage(@Nullable String theiaImage) {
        this.theiaImage = theiaImage;
    }

    public String getBranchRegex() {
        return branchRegex;
    }

    public void setBranchRegex(String branchRegex) {
        this.branchRegex = branchRegex;
    }

    public boolean isAllowBranching() {
        return allowBranching;
    }

    public void setAllowBranching(boolean allowBranching) {
        this.allowBranching = allowBranching;
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

    public boolean isTestwiseCoverageEnabled() {
        return testwiseCoverageEnabled;
    }

    public void setTestwiseCoverageEnabled(boolean testwiseCoverageEnabled) {
        this.testwiseCoverageEnabled = testwiseCoverageEnabled;
    }

    public ProgrammingExercise getProgrammingExercise() {
        return programmingExercise;
    }

    public void setProgrammingExercise(ProgrammingExercise programmingExercise) {
        this.programmingExercise = programmingExercise;
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

    public String getAssignmentCheckoutPath() {
        return assignmentCheckoutPath;
    }

    public void setAssignmentCheckoutPath(String assignmentCheckoutPath) {
        this.assignmentCheckoutPath = assignmentCheckoutPath;
    }

    public String getSolutionCheckoutPath() {
        return solutionCheckoutPath;
    }

    public void setSolutionCheckoutPath(String solutionCheckoutPath) {
        this.solutionCheckoutPath = solutionCheckoutPath;
    }

    /**
     * Converts a JSON string representing Docker flags (in the form of a list of key-value pairs)
     * into a {@link DockerRunConfig} instance.
     *
     * <p>
     * The JSON string is expected to represent a list of key-value pairs where each
     * entry is a list containing two strings: the first being the key and the second being the value.
     * Example JSON input:
     *
     * <pre>
     * [["network", "none"], ["env", "TEST"]]
     * </pre>
     *
     * @return a {@link DockerRunConfig} object initialized with the parsed flags, or {@code null} if an error occurs
     */
    public DockerRunConfig getDockerRunConfig() {
        if (StringUtils.isBlank(dockerFlags)) {
            return null;
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();

            List<List<String>> list = objectMapper.readValue(dockerFlags, new TypeReference<>() {
            });

            DockerRunConfig dockerRunConfig = new DockerRunConfig();
            for (List<String> entry : list) {
                if (entry.size() != 2 || entry.get(0) == null || entry.get(1) == null || entry.get(0).isBlank() || entry.get(1).isBlank()
                        || !DockerRunConfig.AllowedDockerFlags.isAllowed(entry.get(0))) {
                    log.error("Invalid Docker flag entry: {}. Skipping.", entry);
                    continue;
                }
                switch (entry.get(0)) {
                    case "network":
                        dockerRunConfig.setNetworkDisabled(entry.get(1).equals("none"));
                        break;
                    case "env":
                        dockerRunConfig.setEnv(parseEnvVariableString(entry.get(1)));
                        break;
                    default:
                        log.error("Invalid Docker flag entry: {}. Skipping.", entry);
                        break;
                }

            }
            return dockerRunConfig;
        }
        catch (Exception e) {
            log.error("Failed to parse DockerRunConfig from JSON string: {}. Using default settings.", dockerFlags, e);
        }

        return null;
    }

    private List<String> parseEnvVariableString(String envVariableString) {
        Pattern pattern = Pattern.compile(
                // match key-value pairs, where the key can be a single word or a string in single or double quotes
                // key-value pairs are separated by commas
                "(?:'([^']+)'|\"([^\"]+)\"|(\\w+))=(?:'([^']*)'|\"([^\"]*)\"|([^,]+))");

        Matcher matcher = pattern.matcher(envVariableString);

        List<String> envVars = new ArrayList<>();
        while (matcher.find()) {
            String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);

            String value = matcher.group(4) != null ? matcher.group(4) : matcher.group(5) != null ? matcher.group(5) : matcher.group(6);

            envVars.add(key + "=" + value);
        }

        return envVars;
    }

    @Override
    public String toString() {
        return "BuildJobConfig{" + "id=" + getId() + ", sequentialTestRuns=" + sequentialTestRuns + ", branch='" + branch + '\'' + ", buildPlanConfiguration='"
                + buildPlanConfiguration + '\'' + ", buildScript='" + buildScript + '\'' + ", checkoutSolutionRepository=" + checkoutSolutionRepository + ", checkoutPath='"
                + testCheckoutPath + '\'' + ", timeoutSeconds=" + timeoutSeconds + ", dockerFlags='" + dockerFlags + '\'' + ", testwiseCoverageEnabled=" + testwiseCoverageEnabled
                + ", theiaImage='" + theiaImage + '\'' + ", allowBranching=" + allowBranching + ", branchRegex='" + branchRegex + '\'' + '}';
    }
}
