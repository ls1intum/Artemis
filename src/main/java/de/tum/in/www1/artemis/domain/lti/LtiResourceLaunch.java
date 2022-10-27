package de.tum.in.www1.artemis.domain.lti;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;

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

    /**
     * Creates an LtiResourceLaunch entity from an LTI1.3 launch request to be saved in the database
     *
     * @param launchRequest The launch request
     * @return the resource launch entity
     */
    public static LtiResourceLaunch from(Lti13LaunchRequest launchRequest) {
        LtiResourceLaunch launch = new LtiResourceLaunch();
        launch.iss = launchRequest.getIss();
        launch.sub = launchRequest.getSub();
        launch.deploymentId = launchRequest.getDeploymentId();
        launch.resourceLinkId = launchRequest.getResourceLinkId();

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
}
