package de.tum.cit.aet.artemis.core.dto;

import java.util.Map;
import java.util.Objects;

import org.codeability.sharing.plugins.api.util.SecretChecksumCalculator;
import org.springframework.context.annotation.Profile;

/**
 * the sharing info to request a specific exercise from the sharing platform.
 */
@Profile("sharing")
public class SharingInfoDTO {

    /**
     * the (random) basket Token
     */
    private String basketToken;

    /**
     * the callback URL for the basket request
     */
    private String returnURL;

    /**
     * the base URL for the basket request
     */
    private String apiBaseURL;

    /**
     * the base URL for the basket request
     */
    private String checksum;

    /**
     * the index of the request exercise
     */
    private int exercisePosition;

    /**
     * the callback URL for the basket request
     */
    public String getReturnURL() {
        return returnURL;
    }

    /**
     * sets the callback URL for the basket request
     */
    public void setReturnURL(String returnURL) {
        this.returnURL = returnURL;
    }

    /**
     * the base URL for the basket request
     */
    public String getApiBaseURL() {
        return apiBaseURL;
    }

    /**
     * sets the base URL for the basket request
     */
    public void setApiBaseURL(String apiBaseURL) {
        this.apiBaseURL = apiBaseURL;
    }

    /**
     * the (random) basket Token
     */
    public String getBasketToken() {
        return basketToken;
    }

    /**
     * sets the basket Token
     */
    public void setBasketToken(String basketToken) {
        this.basketToken = basketToken;
    }

    /**
     * the index of the request exercise
     */
    public int getExercisePosition() {
        return exercisePosition;
    }

    /**
     * sets the index of the request exercise
     */
    public void setExercisePosition(int exercisePosition) {
        this.exercisePosition = exercisePosition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SharingInfoDTO that = (SharingInfoDTO) o;
        return exercisePosition == that.exercisePosition && Objects.equals(basketToken, that.basketToken) && Objects.equals(returnURL, that.returnURL)
                && Objects.equals(apiBaseURL, that.apiBaseURL);
    }

    @Override
    public int hashCode() {
        return Objects.hash(basketToken, returnURL, apiBaseURL, exercisePosition);
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    /**
     * verifies the checksum of returnURL and apiBaseURL with the secret key
     * @param secretKey the secret key
     * @return true, if checksum is correct
     */
    public boolean checkChecksum(String secretKey) {
        return SecretChecksumCalculator.checkChecksum(Map.of("returnURL", returnURL, "apiBaseURL", apiBaseURL), secretKey, checksum);
    }
}
