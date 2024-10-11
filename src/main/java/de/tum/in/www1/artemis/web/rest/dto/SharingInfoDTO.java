package de.tum.in.www1.artemis.web.rest.dto;

import org.springframework.context.annotation.Profile;

@Profile("sharing")
public class SharingInfoDTO {

    private String basketToken;

    private String returnURL;

    private String apiBaseURL;

    private int exercisePosition;

    public String getReturnURL() {
        return returnURL;
    }

    public void setReturnURL(String returnURL) {
        this.returnURL = returnURL;
    }

    public String getApiBaseURL() {
        return apiBaseURL;
    }

    public void setApiBaseURL(String apiBaseURL) {
        this.apiBaseURL = apiBaseURL;
    }

    public String getBasketToken() {
        return basketToken;
    }

    public void setBasketToken(String basketToken) {
        this.basketToken = basketToken;
    }

    public int getExercisePosition() {
        return exercisePosition;
    }

    public void setExercisePosition(int exercisePosition) {
        this.exercisePosition = exercisePosition;
    }
}
