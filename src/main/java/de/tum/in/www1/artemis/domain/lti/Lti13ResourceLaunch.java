package de.tum.in.www1.artemis.domain.lti;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;

/**
 * Represents a LTI 1.3 Resource Link Launch.
 */
@Entity
@Table(name = "lti13resourcelaunch")
public class Lti13ResourceLaunch extends DomainObject {

    @NotNull
    private String iss;

    @NotNull
    private String sub;

    @NotNull
    private String deploymentId;

    @NotNull
    private String resourceLinkId;

    @NotNull
    private String targetLinkUri;

    private String scoreLineItemUrl;

    @NotNull
    @ManyToOne
    private User user;

    @NotNull
    @ManyToOne
    private Exercise exercise;

    @NotNull
    private String clientRegistrationId;

    public Lti13ResourceLaunch() {
    }

    public static Lti13ResourceLaunch from(Lti13LaunchRequest launchRequest) {
        Lti13ResourceLaunch launch = new Lti13ResourceLaunch();
        launch.iss = launchRequest.getIss();
        launch.sub = launchRequest.getSub();
        launch.deploymentId = launchRequest.getDeploymentId();
        launch.resourceLinkId = launchRequest.getResourceLinkId();
        launch.clientRegistrationId = launchRequest.getClientRegistrationId();

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

    public String getTargetLinkUri() {
        return targetLinkUri;
    }

    public void setTargetLinkUri(String targetLinkUri) {
        this.targetLinkUri = targetLinkUri;
    }

    public String getClientRegistrationId() {
        return clientRegistrationId;
    }

    public void setClientRegistrationId(String clientRegistrationId) {
        this.clientRegistrationId = clientRegistrationId;
    }
}
