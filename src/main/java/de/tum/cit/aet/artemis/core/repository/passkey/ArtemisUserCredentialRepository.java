package de.tum.cit.aet.artemis.core.repository.passkey;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.config.PasskeyEnabled;
import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.PasskeyType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.converter.BytesConverter;
import de.tum.cit.aet.artemis.core.dto.PasskeyDTO;
import de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

/**
 * Repository implementation for managing user credentials in the context of WebAuthn authentication.
 * <p>
 * This class implements the {@link UserCredentialRepository} interface and provides methods to save, delete,
 * and retrieve credential records associated with users. It integrates with the {@link ArtemisPublicKeyCredentialUserEntityRepository},
 * {@link PasskeyCredentialsRepository}, and {@link UserRepository} to manage credential data and user associations.
 * </p>
 * <p>
 * Note: The {@code findByUserId} method, which will be called from Spring Security WebAuthn implementation is not fully implemented due to unclear user ID format.
 * </p>
 *
 * @see UserCredentialRepository
 * @see ArtemisPublicKeyCredentialUserEntityRepository
 * @see PasskeyCredentialsRepository
 * @see UserRepository
 */
@Conditional(PasskeyEnabled.class)
@Lazy
@Repository
public class ArtemisUserCredentialRepository implements UserCredentialRepository {

    private static final Logger log = LoggerFactory.getLogger(ArtemisUserCredentialRepository.class);

    private final ArtemisPublicKeyCredentialUserEntityRepository artemisPublicKeyCredentialUserEntityRepository;

    private final PasskeyCredentialsRepository passkeyCredentialsRepository;

    private final UserRepository userRepository;

    public ArtemisUserCredentialRepository(ArtemisPublicKeyCredentialUserEntityRepository artemisPublicKeyCredentialUserEntityRepository,
            PasskeyCredentialsRepository passkeyCredentialsRepository, UserRepository userRepository) {
        this.artemisPublicKeyCredentialUserEntityRepository = artemisPublicKeyCredentialUserEntityRepository;
        this.passkeyCredentialsRepository = passkeyCredentialsRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void delete(Bytes credentialId) {
        passkeyCredentialsRepository.findByCredentialId(credentialId.toBase64UrlString()).ifPresent(passkeyCredentialsRepository::delete);
    }

    /**
     * Saves a given credential record by associating it with an existing user and persisting it in the repository.
     * <p>
     * If a credential with the same ID already exists, it will be updated with the new data. Otherwise, a new credential
     * will be created and saved.
     * </p>
     *
     * @param credentialRecord the credential record to be saved, containing information about the credential and its associated user
     */
    @Override
    public void save(CredentialRecord credentialRecord) {
        // @formatter:off
        artemisPublicKeyCredentialUserEntityRepository
                .findArtemisUserById(credentialRecord.getUserEntityUserId())
                .ifPresent(user -> {
                    passkeyCredentialsRepository
                            .findByCredentialId(credentialRecord.getCredentialId().toBase64UrlString())
                            .map(existingCredential ->
                                    passkeyCredentialsRepository.save(
                                            toPasskeyCredential(existingCredential, credentialRecord, user)
                                    )
                            )
                            .orElseGet(() ->
                                    passkeyCredentialsRepository.save(
                                            toPasskeyCredential(credentialRecord, user)
                                    )
                            );
                });
        // @formatter:on
    }

    /**
     * @param credentialId which is expected to equal the {@link User#getExternalId()}
     */
    @Override
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        return passkeyCredentialsRepository.findByCredentialId(credentialId.toBase64UrlString()).map(PasskeyCredential::toCredentialRecord).orElseGet(() -> {
            log.debug("No credential found for id={}", credentialId.toBase64UrlString());
            return null;
        });
    }

    /**
     * <p>
     * Finds all credential records associated with a given user ID.
     * </p>
     * <p>
     * <i>Note: {@link ArtemisPublicKeyCredentialUserEntityRepository#save} will be called on authentication with passkey by SpringSecurity
     * but this does not seem to contain useful information. In the case an exception will be thrown, as the random id is not a long, which
     * we expect for our user ids.<br>
     * Therefore, we handle the error of an invalid id format gracefully and return an empty list.</i>
     * </p>
     *
     * @param userId to search for a user.
     * @return list of {@link CredentialRecord} associated with the user.
     */
    @Override
    public List<CredentialRecord> findByUserId(Bytes userId) {
        try {
            Optional<User> user = userRepository.findById(BytesConverter.bytesToLong(userId));

            return user.map(passkeyUser -> passkeyCredentialsRepository.findByUser(passkeyUser.getId()).stream().map(PasskeyCredential::toCredentialRecord).toList())
                    .orElseGet(List::of);
        }
        catch (IllegalArgumentException e) {
            // this will occur on authentication as the userId is set to a random id there
            // we might want to customize the implementation, so neither the ArtemisPublicKeyCredentialUserEntityRepository#save
            // method nor the ArtemisUserCredentialRepository#findByUserId method is called during passkey authentication
            log.debug("Cannot convert WebAuthn userId '{}' to long – returning empty credential list", userId.toBase64UrlString(), e);
            return List.of(); // for now, we just return an empty list when an unknown userId format is passed
        }
    }

    /**
     * Finds all passkey credentials for the given user ID and returns them as DTOs.
     *
     * <p>
     * The method performs the following steps:
     * <ul>
     * <li>Looks up the {@link User} entity based on the given {@code userId} (converted from {@link Bytes} to {@code long}).</li>
     * <li>If the user exists, fetches associated {@link PasskeyCredential} entities and maps them to {@link CredentialRecord}.</li>
     * <li>Converts each {@link CredentialRecord} to a simplified {@link PasskeyDTO} containing only essential metadata.</li>
     * </ul>
     *
     * @param userId the user's ID as a {@link Bytes} object (WebAuthn-compatible identifier).
     * @return a list of {@link PasskeyDTO} representing passkey credentials for the user; an empty list if the user is not found.
     */
    public List<PasskeyDTO> findPasskeyDtosByUserId(Bytes userId) {
        Optional<User> user = userRepository.findById(BytesConverter.bytesToLong(userId));

        return user.map(passkeyUser -> passkeyCredentialsRepository.findByUser(passkeyUser.getId()).stream().map(PasskeyCredential::toDto).toList()).orElseGet(List::of);
    }

    /**
     * Updates an existing {@link PasskeyCredential} entity with the data from a {@link CredentialRecord} and links it to the specified {@link User}.
     *
     * <p>
     * This method is useful when reusing an existing {@code PasskeyCredential} instance (e.g., from persistence context)
     * and updating it with fresh data from the credential record.
     *
     * @param credential       the {@link PasskeyCredential} instance to update.
     * @param credentialRecord the data source containing credential metadata and WebAuthn information.
     * @param user             the {@link User} entity to associate the credential with.
     * @return the updated {@link PasskeyCredential} instance.
     */
    public static PasskeyCredential toPasskeyCredential(PasskeyCredential credential, CredentialRecord credentialRecord, User user) {
        credential.setUser(user);
        credential.setLabel(credentialRecord.getLabel());
        credential.setCredentialType(PasskeyType.fromLabel(credentialRecord.getCredentialType().getValue()));
        credential.setCredentialId(credentialRecord.getCredentialId().toBase64UrlString());
        credential.setPublicKeyCose(credentialRecord.getPublicKey());
        credential.setSignatureCount(credentialRecord.getSignatureCount());
        credential.setUvInitialized(credentialRecord.isUvInitialized());
        credential.setTransports(credentialRecord.getTransports());
        credential.setBackupEligible(credentialRecord.isBackupEligible());
        credential.setBackupState(credentialRecord.isBackupState());
        credential.setAttestationObject(credentialRecord.getAttestationObject());
        credential.setLastUsed(credentialRecord.getLastUsed());
        credential.setCreatedDate(credentialRecord.getCreated());

        return credential;
    }

    /**
     * Creates a new {@link PasskeyCredential} instance initialized with the data from the given {@link CredentialRecord}
     * and links it to the specified {@link User}.
     *
     * @param credentialRecord the record containing credential data to initialize the entity.
     * @param user             the {@link User} to associate the credential with.
     * @return a new {@link PasskeyCredential} populated from the credential record.
     */
    private static PasskeyCredential toPasskeyCredential(CredentialRecord credentialRecord, User user) {
        return toPasskeyCredential(new PasskeyCredential(), credentialRecord, user);
    }
}
