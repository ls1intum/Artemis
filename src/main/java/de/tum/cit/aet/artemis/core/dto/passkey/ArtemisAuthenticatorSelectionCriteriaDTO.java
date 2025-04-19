package de.tum.cit.aet.artemis.core.dto.passkey;

import java.io.Serializable;
import java.util.Objects;

import org.springframework.security.web.webauthn.api.AuthenticatorAttachment;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * We cannot directly use the SpringSecurity object as it is not serializable.
 *
 * @see org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ArtemisAuthenticatorSelectionCriteriaDTO(AuthenticatorAttachment authenticatorAttachment, String residentKey, UserVerificationRequirement userVerification)
        implements Serializable {

    public ArtemisAuthenticatorSelectionCriteriaDTO(AuthenticatorAttachment authenticatorAttachment, String residentKey, UserVerificationRequirement userVerification) {
        this.authenticatorAttachment = authenticatorAttachment;
        this.residentKey = Objects.requireNonNullElse(residentKey, ResidentKeyRequirement.DISCOURAGED).toString();
        this.userVerification = userVerification;
    }

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
