package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.PasskeyDto;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.repository.webauthn.ArtemisUserCredentialRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;

/**
 * REST controller for public endpoints regarding the webauthn (Web Authentication) API, e.g. used for passkeys.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/core/passkey/")
public class PasskeyResource {

    public static final String ENTITY_NAME = "passkey";

    private static final Logger log = LoggerFactory.getLogger(PasskeyResource.class);

    private final UserRepository userRepository;

    private final ArtemisUserCredentialRepository artemisUserCredentialRepository;

    public PasskeyResource(UserRepository userRepository, ArtemisUserCredentialRepository artemisUserCredentialRepository) {
        this.userRepository = userRepository;
        this.artemisUserCredentialRepository = artemisUserCredentialRepository;
    }

    @GetMapping("/user")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PasskeyDto>> getPasskeys() {
        var user = userRepository.getUser();
        log.info("Retrieving passkeys for user with id: {}", user.getId());

        List<PasskeyDto> passkeys = artemisUserCredentialRepository.findPasskeyDtosByUserId(User.longToBytes(user.getId()));

        return ResponseEntity.ok(passkeys);
    }

    /**
     * @param credentialIdBase64Encoded as base64 encoded url string
     */
    @DeleteMapping("{credentialIdBase64Encoded}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deletePasskey(@PathVariable String credentialIdBase64Encoded) {
        log.info("Deleting passkey with id: {}", credentialIdBase64Encoded);

        Bytes credentialId = Bytes.fromBase64(credentialIdBase64Encoded);
        artemisUserCredentialRepository.delete(credentialId);

        return ResponseEntity.noContent().build();
    }
}
