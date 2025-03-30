package de.tum.cit.aet.artemis.core.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.webauthn4j.springframework.security.credential.WebAuthnCredentialRecord;
import com.webauthn4j.springframework.security.credential.WebAuthnCredentialRecordManager;
import com.webauthn4j.springframework.security.exception.CredentialIdNotFoundException;
import com.webauthn4j.util.Base64UrlUtil;

import de.tum.cit.aet.artemis.core.domain.PasskeyCredentials;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.programming.repository.PasskeyCredentialsRepository;

@Service
public class PassKeyCredentialService implements WebAuthnCredentialRecordManager {

    private PasskeyCredentialsRepository authenticatorEntityRepository;

    @Autowired
    public void setPasskeyCredentialsRepository(PasskeyCredentialsRepository authenticatorEntityRepository) {
        this.authenticatorEntityRepository = authenticatorEntityRepository;
    }

    private final UserRepository userRepository;

    public PassKeyCredentialService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void updateCounter(byte[] credentialId, long counter) throws CredentialIdNotFoundException {
        PasskeyCredentials authenticatorEntity = authenticatorEntityRepository.findOneByCredentialId(credentialId)
                .orElseThrow(() -> new CredentialIdNotFoundException("AuthenticatorEntity not found"));
        authenticatorEntity.setCounter(counter);
    }

    @Override
    public WebAuthnCredentialRecord loadCredentialRecordByCredentialId(byte[] credentialId) {
        return authenticatorEntityRepository.findOneByCredentialId(credentialId).orElseThrow(() -> new CredentialIdNotFoundException("AuthenticatorEntity not found"));
    }

    @Override
    public List<WebAuthnCredentialRecord> loadCredentialRecordsByUserPrincipal(Object principal) {
        // FIX ME (use the proper principal and proper find all method)
        String username;
        if (principal == null) {
            return Collections.emptyList();
        }
        else if (principal instanceof String) {
            username = (String) principal;
        }
        else if (principal instanceof Authentication) {
            username = ((Authentication) principal).getName();
        }
        else {
            throw new IllegalArgumentException("unexpected principal is specified.");
        }
        return new ArrayList<>(authenticatorEntityRepository.findAllByEmailAddress(username));
    }

    @Override
    public void createCredentialRecord(WebAuthnCredentialRecord webAuthnCredentialRecord) {
        String userPrincipal = (String) webAuthnCredentialRecord.getUserPrincipal();
        User user = userRepository.findOneByLogin(userPrincipal).orElseThrow(() -> new EntityNotFoundException("User", userPrincipal));

        PasskeyCredentials passkeyCredentials = new PasskeyCredentials();
        passkeyCredentials.setName(user.getName());
        passkeyCredentials.setUser(user);
        passkeyCredentials.setCounter(webAuthnCredentialRecord.getCounter());
        passkeyCredentials.setTransports(webAuthnCredentialRecord.getTransports());
        passkeyCredentials.setAttestedCredentialData(webAuthnCredentialRecord.getAttestedCredentialData());
        passkeyCredentials.setAttestationStatement(webAuthnCredentialRecord.getAttestationStatement());
        authenticatorEntityRepository.save(passkeyCredentials);
    }

    @Override
    public void deleteCredentialRecord(byte[] credentialId) {
        var credentialIdString = Base64UrlUtil.encodeToString(credentialId);
        authenticatorEntityRepository.deleteById(credentialIdString);
    }

    @Override
    public boolean credentialRecordExists(byte[] credentialId) {
        var credentialIdString = Base64UrlUtil.encodeToString(credentialId);
        return authenticatorEntityRepository.existsById(credentialIdString);
    }
}
