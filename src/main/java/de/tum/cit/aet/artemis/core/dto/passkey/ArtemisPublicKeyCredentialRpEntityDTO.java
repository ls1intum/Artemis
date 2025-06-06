package de.tum.cit.aet.artemis.core.dto.passkey;

import java.io.Serializable;

import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * We cannot directly use the SpringSecurity object as it is not serializable.
 *
 * @see org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ArtemisPublicKeyCredentialRpEntityDTO(String name, String id) implements Serializable {

    public PublicKeyCredentialRpEntity toPublicKeyCredentialRpEntity() {
        return PublicKeyCredentialRpEntity.builder().name(name()).id(id()).build();
    }

}
