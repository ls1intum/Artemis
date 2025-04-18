package de.tum.cit.aet.artemis.core.dto;

import java.io.Serializable;

/**
 * @see org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity
 */
public record ArtemisPublicKeyCredentialRpEntityDTO(String name, String id) implements Serializable {

}
