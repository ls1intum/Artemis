package de.tum.cit.aet.artemis.core.authentication;

import org.springframework.security.web.webauthn.api.CredentialRecord;

import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.core.repository.passkey.ArtemisUserCredentialRepository;

/**
 * Factory for creating {@link PasskeyCredential} objects for testing purposes.
 */
public class PasskeyCredentialFactory {

    private PasskeyCredentialFactory() {
        // Prevent instantiation of this utility class
    }

    /**
     * Creates and saves a PasskeyCredential for testing purposes.
     * <p>
     * This method creates a credential record, saves it through the repository,
     * and optionally sets the super admin approval status.
     * </p>
     *
     * @param user                            the user to associate with the credential
     * @param label                           the label for the credential
     * @param isSuperAdminApproved            whether the passkey should be marked as super admin approved
     * @param artemisUserCredentialRepository the repository for saving credentials
     * @param passkeyCredentialsRepository    the repository for finding saved credentials
     * @return a PasskeyCredential instance that has been saved to the database
     */
    public static PasskeyCredential createAndSavePasskey(User user, String label, boolean isSuperAdminApproved, ArtemisUserCredentialRepository artemisUserCredentialRepository,
            PasskeyCredentialsRepository passkeyCredentialsRepository) {
        CredentialRecord credentialRecord = CredentialRecordFactory.createCredentialRecord(user, label);
        artemisUserCredentialRepository.save(credentialRecord);
        PasskeyCredential savedPasskey = passkeyCredentialsRepository.findByCredentialId(credentialRecord.getCredentialId().toBase64UrlString()).orElseThrow();
        savedPasskey.setSuperAdminApproved(isSuperAdminApproved);
        passkeyCredentialsRepository.save(savedPasskey);

        return savedPasskey;
    }
}
