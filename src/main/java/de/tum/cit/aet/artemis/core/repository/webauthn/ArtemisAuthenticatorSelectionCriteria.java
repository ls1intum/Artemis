package de.tum.cit.aet.artemis.core.repository.webauthn;

import java.io.Serializable;

import org.springframework.security.web.webauthn.api.AuthenticatorAttachment;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;

/**
 * @see org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
 */
public record ArtemisAuthenticatorSelectionCriteria(AuthenticatorAttachment authenticatorAttachment, String residentKey, UserVerificationRequirement userVerification)
        implements Serializable {
}
