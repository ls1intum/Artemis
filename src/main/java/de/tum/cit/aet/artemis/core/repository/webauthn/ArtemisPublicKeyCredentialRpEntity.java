package de.tum.cit.aet.artemis.core.repository.webauthn;

import java.io.Serializable;

/**
 * @see org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity
 */
public record ArtemisPublicKeyCredentialRpEntity(String name, String id) implements Serializable {

}
