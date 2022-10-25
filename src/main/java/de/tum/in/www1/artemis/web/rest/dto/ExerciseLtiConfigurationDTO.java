package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Created by Josias Montag on 26.09.16.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseLtiConfigurationDTO {

    private String launchUrl;

    private String ltiKey;

    private String ltiSecret;

    public ExerciseLtiConfigurationDTO(String launchUrl, String ltiKey, String ltiSecret) {
        this.launchUrl = launchUrl;
        this.ltiKey = ltiKey;
        this.ltiSecret = ltiSecret;
    }

    public String getLaunchUrl() {
        return launchUrl;
    }

    public void setLaunchUrl(String launchUrl) {
        this.launchUrl = launchUrl;
    }

    public String getLtiKey() {
        return ltiKey;
    }

    public void setLtiKey(String ltiKey) {
        this.ltiKey = ltiKey;
    }

    public String getLtiSecret() {
        return ltiSecret;
    }

    public void setLtiSecret(String ltiSecret) {
        this.ltiSecret = ltiSecret;
    }
}
