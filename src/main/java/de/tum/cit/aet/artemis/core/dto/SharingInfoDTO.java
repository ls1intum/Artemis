package de.tum.cit.aet.artemis.core.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

import org.codeability.sharing.plugins.api.util.SecretChecksumCalculator;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * the sharing info to request a specific exercise from the sharing platform.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SharingInfoDTO(@NotNull String basketToken, @NotNull String returnURL, @NotNull String apiBaseURL, @NotNull String checksum, int exercisePosition) {

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
