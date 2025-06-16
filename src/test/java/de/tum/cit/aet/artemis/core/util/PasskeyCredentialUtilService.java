package de.tum.cit.aet.artemis.core.util;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.time.Instant;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.PasskeyType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository;

/**
 * Service responsible for initializing the database with specific testdata related to {@link de.tum.cit.aet.artemis.core.domain.PasskeyCredential} for use in integration tests.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class PasskeyCredentialUtilService {

    private static final int DEFAULT_CREDENTIAL_PUBLIC_KEY_BYTE_COUNT = 77;

    private static final int DEFAULT_CREDENTIAL_ID_BYTE_COUNT = 32;

    @Autowired
    private PasskeyCredentialsRepository passkeyCredentialTestRepository;

    private byte[] getRandomBytes(int length) {
        byte[] bytes = new byte[length];
        new java.security.SecureRandom().nextBytes(bytes);
        return bytes;
    }

    public PasskeyCredential createAndSavePasskeyCredential(User user) {
        PasskeyCredential newCredential = new PasskeyCredential();
        newCredential.setUser(user);
        newCredential.setLabel("Default Passkey Label");
        newCredential.setCredentialType(PasskeyType.PUBLIC_KEY);
        newCredential.setCredentialId(java.util.UUID.randomUUID().toString());
        newCredential.setPublicKeyCose(new ImmutablePublicKeyCose(getRandomBytes(DEFAULT_CREDENTIAL_PUBLIC_KEY_BYTE_COUNT)));
        newCredential.setSignatureCount(0);
        newCredential.setUvInitialized(true);
        newCredential.setBackupEligible(true);
        newCredential.setBackupState(true);
        newCredential.setTransports(Set.of(AuthenticatorTransport.INTERNAL, AuthenticatorTransport.HYBRID));
        newCredential.setAttestationObject(new Bytes(getRandomBytes(DEFAULT_CREDENTIAL_ID_BYTE_COUNT)));
        newCredential.setLastUsed(Instant.now());

        passkeyCredentialTestRepository.save(newCredential);

        return newCredential;
    }
}
