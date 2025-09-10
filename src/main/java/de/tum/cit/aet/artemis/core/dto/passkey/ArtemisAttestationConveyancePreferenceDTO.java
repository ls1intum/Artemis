package de.tum.cit.aet.artemis.core.dto.passkey;

import java.io.Serializable;

import org.springframework.security.web.webauthn.api.AttestationConveyancePreference;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * We cannot directly use the SpringSecurity object as it is not serializable.
 *
 * @see org.springframework.security.web.webauthn.api.AttestationConveyancePreference
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ArtemisAttestationConveyancePreferenceDTO(String value) implements Serializable {

    public AttestationConveyancePreference toAttestationConveyancePreference() {
        return AttestationConveyancePreference.valueOf(value);
    }
}
