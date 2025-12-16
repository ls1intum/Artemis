package de.tum.cit.aet.artemis.programming.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;

@Entity
@Table(name = "docker_container_config")
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class DockerContainerConfig extends DomainObject {

    private static final Logger log = LoggerFactory.getLogger(DockerContainerConfig.class);

    @Column(name = "config_name")
    // TODO: Temp passing through duplicately @JsonIgnore
    private String name;

    @Column(name = "build_plan_configuration", columnDefinition = "longtext")
    private String buildPlanConfiguration;

    @Column(name = "build_script", columnDefinition = "longtext")
    private String buildScript;

    @Column(name = "docker_flags", columnDefinition = "longtext")
    private String dockerFlags;

    @ManyToOne
    @JoinColumn(name = "build_config_id")
    @JsonIgnore
    private ProgrammingExerciseBuildConfig buildConfig;

    public DockerContainerConfig() {

    }

    public DockerContainerConfig(DockerContainerConfig originalContainerConfig, ProgrammingExerciseBuildConfig buildConfig) {
        this.name = originalContainerConfig.getName();
        this.buildPlanConfiguration = originalContainerConfig.getBuildPlanConfiguration();
        this.buildScript = originalContainerConfig.getBuildScript();
        this.dockerFlags = originalContainerConfig.getDockerFlags();
        this.buildConfig = buildConfig;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getDockerFlags() {
        return dockerFlags;
    }

    public void setDockerFlags(String dockerFlags) {
        this.dockerFlags = dockerFlags;
    }

    public ProgrammingExerciseBuildConfig getBuildConfig() {
        return buildConfig;
    }

    public void setBuildConfig(ProgrammingExerciseBuildConfig buildConfig) {
        this.buildConfig = buildConfig;
    }

    /**
     * We store the build plan configuration as a JSON string in the database, as it is easier to handle than a complex object structure.
     * This method parses the JSON string and returns a {@link Windfile} object.
     *
     * @return the {@link Windfile} object or null if the JSON string could not be parsed
     */
    public Windfile getWindfile() {
        if (getBuildPlanConfiguration() == null) {
            return null;
        }
        try {
            return Windfile.deserialize(getBuildPlanConfiguration());
        }
        catch (JsonProcessingException e) {
            log.error("Could not parse build plan configuration for programming exercise {}", this.getId(), e);
        }
        return null;
    }

    @Override
    public String toString() {
        return "DockerContainerConfig{" + "id=" + getId() + ", buildPlanConfiguration='" + getBuildPlanConfiguration() + '\'' + ", buildScript='" + getBuildScript()
                + ", dockerFlags='" + getDockerFlags() + '\'' + '}';
    }
}
