package de.tum.cit.aet.artemis.core.dto;

import java.io.Serializable;

// TODO add validator?

/**
 * @see org.springframework.security.web.webauthn.api.AttestationConveyancePreference
 */
public record ArtemisAttestationConveyancePreferenceDTO(String value) implements Serializable {

}
