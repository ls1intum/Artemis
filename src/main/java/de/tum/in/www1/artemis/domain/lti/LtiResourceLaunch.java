package de.tum.in.www1.artemis.domain.lti;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.jspecify.annotations.NonNull;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LtiPlatformConfiguration;
import de.tum.in.www1.artemis.domain.User;

/**
 * Represents an LTI 1.3 Resource Link Launch.
 */
@Entity
@Table(name = "lti_resource_launch")
public class LtiResourceLaunch extends DomainObject {

    @NonNull
    private String iss;

    @NonNull
    private String sub;

    @NonNull
    private String deploymentId;

    @NonNull
    private String resourceLinkId;

    private String scoreLineItemUrl;

    @NonNull
    @ManyToOne
    private User user;

    @NonNull
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
