package de.tum.cit.aet.artemis.core.dto;

import java.io.Serializable;

import org.springframework.security.web.webauthn.api.AuthenticatorAttachment;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;

/**
 * @see org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria
 */
public record ArtemisAuthenticatorSelectionCriteriaDTO(AuthenticatorAttachment authenticatorAttachment, String residentKey, UserVerificationRequirement userVerification)
        implements Serializable {
}
