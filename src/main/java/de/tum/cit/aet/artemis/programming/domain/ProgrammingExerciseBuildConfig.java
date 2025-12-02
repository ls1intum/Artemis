package de.tum.cit.aet.artemis.programming.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;

@Entity
@Table(name = "programming_exercise_build_config")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// We dont want to expose the programming exercise in the build config
@JsonIgnoreProperties(value = { "programmingExercise" })
public class ProgrammingExerciseBuildConfig extends DomainObject {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseBuildConfig.class);

    @Column(name = "sequential_test_runs")
    private Boolean sequentialTestRuns;

    @Column(name = "branch")
    private String branch;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "buildConfig", fetch = FetchType.EAGER) // TODO: Cascade type?, Maybe use @transactional instead of eager fetch type.
    @MapKey(name = "name")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("buildConfig")
    private Map<String, DockerContainerConfig> containerConfigs = new HashMap<>();

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

    @OneToOne(mappedBy = "buildConfig")
    private ProgrammingExercise programmingExercise;

    @Nullable
    @Column(name = "theia_image")
    private String theiaImage;

    @Column(name = "allow_branching", columnDefinition = "boolean default false", nullable = false)
    private boolean allowBranching = false; // default value

    @Column(name = "branch_regex", columnDefinition = "varchar(128)")
    @Nullable
    private String branchRegex = ".*"; // default value

    @Size(max = 36)
    @Nullable
    @Column(name = "build_plan_access_secret", length = 36)
    private String buildPlanAccessSecret;

    public ProgrammingExerciseBuildConfig() {
    }

    public ProgrammingExerciseBuildConfig(ProgrammingExerciseBuildConfig originalBuildConfig) {
        this.setBranch(originalBuildConfig.getBranch());

        this.containerConfigs = originalBuildConfig.getContainerConfigs().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new DockerContainerConfig(e.getValue(), this)));

        this.setTestCheckoutPath(originalBuildConfig.getTestCheckoutPath());
        this.setAssignmentCheckoutPath(originalBuildConfig.getAssignmentCheckoutPath());
        this.setSolutionCheckoutPath(originalBuildConfig.getSolutionCheckoutPath());
        this.setCheckoutSolutionRepository(originalBuildConfig.getCheckoutSolutionRepository());
        this.setSequentialTestRuns(originalBuildConfig.hasSequentialTestRuns());
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
     *
     * @return the name of the default branch or null if not yet stored in Artemis
     */
    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    private DockerContainerConfig getContainerConfig(String configName) {
        if (!containerConfigs.containsKey(configName)) {
            DockerContainerConfig newConfig = new DockerContainerConfig();
            newConfig.setName(configName);
            newConfig.setBuildConfig(this);
            containerConfigs.put(configName, newConfig);
        }
        return containerConfigs.get(configName);
    }

    /**
     * TODO
     *
     */
    @JsonIgnore
    public DockerContainerConfig getDefaultContainerConfig() {
        return getContainerConfig("Container 1");
    }

    /**
     * TODO
     *
     */
    public Map<String, DockerContainerConfig> getContainerConfigs() {
        return containerConfigs;
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

    @Nullable
    public String getTheiaImage() {
        return theiaImage;
    }

    public void setTheiaImage(@Nullable String theiaImage) {
        this.theiaImage = theiaImage;
    }

    /**
     * A regex defining which branches are allowed to be created.
     *
     * <p>
     * Should only be considered if branching is allowed ({@link #isAllowBranching()}).
     * Otherwise, only the default branch for the exercise should be allowed regardless of this regex.
     *
     * @return The branch name regex pattern.
     */
    @Nullable
    public String getBranchRegex() {
        return branchRegex;
    }

    public void setBranchRegex(String branchRegex) {
        this.branchRegex = Objects.requireNonNullElse(branchRegex, ".*");
    }

    public boolean isAllowBranching() {
        return allowBranching;
    }

    public void setAllowBranching(boolean allowBranching) {
        this.allowBranching = allowBranching;
    }

    /**
     * TODO
     *
     */
    @JsonIgnore
    public Windfile getDefaultWindfile() {
        if (getDefaultContainerConfig().getBuildPlanConfiguration() == null) {
            return null;
        }
        try {
            return Windfile.deserialize(getDefaultContainerConfig().getBuildPlanConfiguration());
        }
        catch (JsonProcessingException e) {
            log.error("Could not parse build plan configuration for programming exercise {}", this.getId(), e);
        }
        return null;
    }

    public void filterSensitiveInformation() {
        for (DockerContainerConfig config : containerConfigs.values()) {
            config.setBuildPlanConfiguration(null);
            config.setBuildScript(null);
        }
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

    @Override
    public String toString() {
        String containerConfigsString = containerConfigs.values().stream().map(DockerContainerConfig::toString).collect(Collectors.joining(", "));

        return "BuildJobConfig{" + "id=" + getId() + ", sequentialTestRuns=" + sequentialTestRuns + ", branch='" + branch + '\'' + ", containerConfigs=[" + containerConfigsString
                + "], checkoutSolutionRepository=" + checkoutSolutionRepository + ", checkoutPath='" + testCheckoutPath + '\'' + ", timeoutSeconds=" + timeoutSeconds
                + ", theiaImage='" + theiaImage + '\'' + ", allowBranching=" + allowBranching + ", branchRegex='" + branchRegex + '\'' + '}';
    }
}
