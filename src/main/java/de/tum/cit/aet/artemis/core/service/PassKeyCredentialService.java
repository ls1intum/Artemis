package de.tum.cit.aet.artemis.core.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.webauthn4j.springframework.security.credential.WebAuthnCredentialRecord;
import com.webauthn4j.springframework.security.credential.WebAuthnCredentialRecordManager;
import com.webauthn4j.springframework.security.exception.CredentialIdNotFoundException;
import com.webauthn4j.springframework.security.exception.PrincipalNotFoundException;
import com.webauthn4j.util.Base64UrlUtil;

import de.tum.cit.aet.artemis.core.domain.PasskeyCredentials;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.programming.repository.PasskeyCredentialsRepository;

@Service
public class PassKeyCredentialService implements WebAuthnCredentialRecordManager {

    private static final Logger log = LoggerFactory.getLogger(PassKeyCredentialService.class);

    private final PasskeyCredentialsRepository passkeyCredentialsRepository;

    private final UserRepository userRepository;

    public PassKeyCredentialService(PasskeyCredentialsRepository passkeyCredentialsRepository, UserRepository userRepository) {
        this.passkeyCredentialsRepository = passkeyCredentialsRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void updateCounter(byte[] credentialId, long counter) throws CredentialIdNotFoundException {
        WebAuthnCredentialRecord webAuthnCredentialRecord = this.loadCredentialRecordByCredentialId(credentialId);
        webAuthnCredentialRecord.setCounter(counter);
    }

    @Override
    public WebAuthnCredentialRecord loadCredentialRecordByCredentialId(byte[] credentialId) throws CredentialIdNotFoundException {
        var credentialIdString = Base64UrlUtil.encodeToString(credentialId);
        var passKeyCredentials = passkeyCredentialsRepository.findById(credentialIdString).orElseThrow(() -> new CredentialIdNotFoundException("credentialId not found."));
        return passKeyCredentials.toWebAuthnCredentialRecord();
    }

    @Override
    public List<WebAuthnCredentialRecord> loadCredentialRecordsByUserPrincipal(Object userPrincipal) {
        String principal = (String) userPrincipal;
        log.info("loadCredentialRecordsByUserPrincipal {}", principal);
        User user = userRepository.findOneByLogin(principal).orElseThrow(() -> new EntityNotFoundException("User", principal));
        log.info("Loaded credential records by user {}", user);
        var passkeyCredentials = passkeyCredentialsRepository.findByUserId(user.getId());
        log.info("passkeyCredentials {}", passkeyCredentials);
        if (passkeyCredentials == null || passkeyCredentials.isEmpty()) {
            throw new PrincipalNotFoundException("principal not found.");
        }
        return passkeyCredentials.stream().map(PasskeyCredentials::toWebAuthnCredentialRecord).toList();
    }

    @Override
    public void createCredentialRecord(WebAuthnCredentialRecord webAuthnCredentialRecord) {
        String userPrincipal = (String) webAuthnCredentialRecord.getUserPrincipal();
        User user = userRepository.findOneByLogin(userPrincipal).orElseThrow(() -> new EntityNotFoundException("User", userPrincipal));
        var credentials = PasskeyCredentials.fromWebAuthnCredentialRecord(webAuthnCredentialRecord, user);
        passkeyCredentialsRepository.save(credentials);
    }

    @Override
    public void deleteCredentialRecord(byte[] credentialId) {
        var credentialIdString = Base64UrlUtil.encodeToString(credentialId);
        passkeyCredentialsRepository.deleteById(credentialIdString);
    }

    @Override
    public boolean credentialRecordExists(byte[] credentialId) {
        var credentialIdString = Base64UrlUtil.encodeToString(credentialId);
        return passkeyCredentialsRepository.existsById(credentialIdString);
    }
}
