package de.tum.cit.aet.artemis.core.repository.webauthn;

import java.io.Serializable;

// TODO add validator?

/**
 * @see org.springframework.security.web.webauthn.api.AttestationConveyancePreference
 */
public record ArtemisAttestationConveyancePreference(String value) implements Serializable {

}
