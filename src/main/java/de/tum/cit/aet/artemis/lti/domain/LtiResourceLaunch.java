package de.tum.cit.aet.artemis.lti.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lti.dto.Lti13LaunchRequest;

/**
 * Represents an LTI 1.3 Resource Link Launch.
 */
@Entity
@Table(name = "lti_resource_launch")
public class LtiResourceLaunch extends DomainObject {

    @NotNull
    private String iss;

    @NotNull
    private String sub;

    @NotNull
    private String deploymentId;

    @NotNull
    private String resourceLinkId;

    private String scoreLineItemUrl;

    @NotNull
    @ManyToOne
    private User user;

    @NotNull
    @ManyToOne
    private Exercise exercise;

    @ManyToOne
    private LtiPlatformConfiguration ltiPlatformConfiguration;

    /**
     * Creates an LtiResourceLaunch entity from an LTI1.3 launch request to be saved in the database
     *
     * @param launchRequest The launch request
     * @return the resource launch entity
     */
    public static LtiResourceLaunch from(Lti13LaunchRequest launchRequest) {
        LtiResourceLaunch launch = new LtiResourceLaunch();
        launch.iss = launchRequest.iss();
        launch.sub = launchRequest.sub();
        launch.deploymentId = launchRequest.deploymentId();
        launch.resourceLinkId = launchRequest.resourceLinkId();

        return launch;
    }

    public String getIss() {
        return iss;
    }

    public String getSub() {
        return sub;
    }

    public void setSub(String sub) {
        this.sub = sub;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public String getResourceLinkId() {
        return resourceLinkId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public String getScoreLineItemUrl() {
        return scoreLineItemUrl;
    }

    public void setScoreLineItemUrl(String lineItemUrl) {
        this.scoreLineItemUrl = lineItemUrl;
    }

    public LtiPlatformConfiguration getLtiPlatformConfiguration() {
        return ltiPlatformConfiguration;
    }

    public void setLtiPlatformConfiguration(LtiPlatformConfiguration ltiPlatformConfiguration) {
        this.ltiPlatformConfiguration = ltiPlatformConfiguration;
    }
}
