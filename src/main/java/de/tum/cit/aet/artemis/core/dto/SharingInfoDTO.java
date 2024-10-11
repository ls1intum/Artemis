package de.tum.cit.aet.artemis.core.dto;

import java.util.Map;
import java.util.Objects;

import org.codeability.sharing.plugins.api.util.SecretChecksumCalculator;
import org.springframework.context.annotation.Profile;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * the sharing info to request a specific exercise from the sharing platform.
 */
@Profile("sharing")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SharingInfoDTO(

        /**
         * the (random) basket Token
         */
        String basketToken,

        /**
         * the callback URL for the basket request
         */
        String returnURL,

        /**
         * the base URL for the basket request
         */
        String apiBaseURL,

        /**
         * the base URL for the basket request
         */
        String checksum,

        /**
         * the index of the request exercise
         */
        int exercisePosition) {

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

    /**
     * verifies the checksum of returnURL and apiBaseURL with the secret key
     *
     * @param secretKey the secret key
     * @return true, if checksum is correct
     */
    public boolean checkChecksum(String secretKey) {
        return SecretChecksumCalculator.checkChecksum(Map.of("returnURL", returnURL, "apiBaseURL", apiBaseURL), secretKey, checksum);
    }
}
