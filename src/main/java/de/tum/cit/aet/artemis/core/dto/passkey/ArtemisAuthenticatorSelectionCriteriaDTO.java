package de.tum.cit.aet.artemis.core.dto.passkey;

import java.io.Serializable;
import java.util.Objects;

import org.springframework.security.web.webauthn.api.AuthenticatorAttachment;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Serializable DTO for {@link AuthenticatorSelectionCriteria}, which is used in the WebAuthn
 * registration process to guide authenticator selection. The original class is not serializable,
 * so this DTO allows storing or transmitting selection preferences (e.g., via Hazelcast).
 *
 * <p>
 * This DTO supports conversion back to {@link AuthenticatorSelectionCriteria} for use
 * in WebAuthn operations.
 *
 * @param authenticatorAttachment Indicates whether a platform or cross-platform authenticator is preferred.
 * @param residentKey             String representation of {@link ResidentKeyRequirement}, e.g., "REQUIRED", "DISCOURAGED".
 * @param userVerification        Indicates whether and how the user must be verified during authentication.
 *
 * @see org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ArtemisAuthenticatorSelectionCriteriaDTO(AuthenticatorAttachment authenticatorAttachment, String residentKey, UserVerificationRequirement userVerification)
        implements Serializable {

    /**
     * Constructs a new DTO instance with optional fallback for the resident key requirement.
     *
     * @param authenticatorAttachment The preferred type of authenticator attachment (e.g., platform, cross-platform).
     * @param residentKey             The resident key requirement as a string; defaults to "DISCOURAGED" if null.
     * @param userVerification        The user verification requirement.
     */
    public ArtemisAuthenticatorSelectionCriteriaDTO(AuthenticatorAttachment authenticatorAttachment, String residentKey, UserVerificationRequirement userVerification) {
        this.authenticatorAttachment = authenticatorAttachment;
        this.residentKey = Objects.requireNonNullElse(residentKey, ResidentKeyRequirement.DISCOURAGED).toString();
        this.userVerification = userVerification;
    }

    /**
     * Converts this DTO into a {@link AuthenticatorSelectionCriteria} object for use
     * in WebAuthn credential creation requests.
     *
     * @return a new {@link AuthenticatorSelectionCriteria} instance with values from this DTO.
     */
    public AuthenticatorSelectionCriteria toAuthenticatorSelectionCriteria() {
        // @formatter:off
        return AuthenticatorSelectionCriteria.builder()
            .authenticatorAttachment(authenticatorAttachment())
            .residentKey(ResidentKeyRequirement.valueOf(residentKey()))
            .userVerification(userVerification())
            .build();
        // @formatter:on
    }
}
