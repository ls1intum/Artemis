package de.tum.cit.aet.artemis.core.repository.webauthn;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.PasskeyType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.converter.BytesConverter;
import de.tum.cit.aet.artemis.core.dto.PasskeyDto;
import de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@Profile(PROFILE_CORE)
@Repository
public class ArtemisUserCredentialRepository implements UserCredentialRepository {

    private static final Logger log = LoggerFactory.getLogger(ArtemisUserCredentialRepository.class);

    private final PasskeyCredentialsRepository passkeyCredentialsRepository;

    private final UserRepository userRepository;

    private final ArtemisPublicKeyCredentialUserEntityRepository artemisPublicKeyCredentialUserEntityRepository;

    public ArtemisUserCredentialRepository(PasskeyCredentialsRepository passkeyCredentialsRepository, UserRepository userRepository,
            ArtemisPublicKeyCredentialUserEntityRepository artemisPublicKeyCredentialUserEntityRepository) {
        this.passkeyCredentialsRepository = passkeyCredentialsRepository;
        this.userRepository = userRepository;
        this.artemisPublicKeyCredentialUserEntityRepository = artemisPublicKeyCredentialUserEntityRepository;
    }

    @Override
    public void delete(Bytes credentialId) {
        log.info("delete: id={}", credentialId.toBase64UrlString());
        passkeyCredentialsRepository.findByCredentialId(credentialId.toBase64UrlString()).ifPresent(passkeyCredentialsRepository::delete);
    }

    @Override
    public void save(CredentialRecord credentialRecord) {
        artemisPublicKeyCredentialUserEntityRepository.findArtemisUserById(credentialRecord.getUserEntityUserId()).ifPresent(user -> {
            passkeyCredentialsRepository.findByCredentialId(credentialRecord.getCredentialId().toBase64UrlString())
                    .map((existingCredential) -> passkeyCredentialsRepository.save(toPasskeyCredential(existingCredential, credentialRecord, user)))
                    .orElseGet(() -> passkeyCredentialsRepository.save(toPasskeyCredential(credentialRecord, user)));

            log.info("save: user={}, externalId={}, label={}", user.getName(), user.getExternalId(), credentialRecord.getLabel());
        });
        log.debug("Saving user credential record: {}", credentialRecord);
    }

    /**
     * @param credentialId which is expected to equal the {@link User#getExternalId()}
     */
    @Override
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        log.info("findByCredentialId: id={}", credentialId.toBase64UrlString());

        return passkeyCredentialsRepository.findByCredentialId(credentialId.toBase64UrlString()).map(credential -> {
            User user = userRepository.findById(Objects.requireNonNull(credential.getUser().getId())).orElseThrow();
            return toCredentialRecord(credential, user.getExternalId());
        }).orElse(null);
    }

    @Override
    public List<CredentialRecord> findByUserId(Bytes userId) {
        log.info("findByUserId: userId={}", userId);

        // FIXME - the userId format is not clear yet and seems to change after server startup
        // (in /webauthn/authenticate/options request), we need to find out how to convert it to the externalId it also seems to be
        log.warn("findByUserId not implemented yet, the format of the userId is not clear yet");
        // Optional<User> user = userRepository.findOneByLogin(userId.toBase64UrlString());
        Optional<User> user = Optional.empty();

        return user.map(
                passkeyUser -> passkeyCredentialsRepository.findByUser(passkeyUser.getId()).stream().map(cred -> toCredentialRecord(cred, passkeyUser.getExternalId())).toList())
                .orElseGet(List::of);
    }

    public List<PasskeyDto> findPasskeyDtosByUserId(Bytes userId) {
        log.info("findPasskeyDtosByUserId: userId={}", userId);

        Optional<User> user = userRepository.findById(BytesConverter.bytesToLong(userId));

        List<CredentialRecord> credentialRecords = user.map(
                passkeyUser -> passkeyCredentialsRepository.findByUser(passkeyUser.getId()).stream().map(cred -> toCredentialRecord(cred, passkeyUser.getExternalId())).toList())
                .orElseGet(List::of);

        return credentialRecords.stream()
                .map(credential -> new PasskeyDto(credential.getCredentialId().toBase64UrlString(), credential.getLabel(), credential.getCreated(), credential.getLastUsed()))
                .toList();
    }

    private static CredentialRecord toCredentialRecord(PasskeyCredential credential, Bytes userId) {
        // @formatter:off
        return ImmutableCredentialRecord.builder()
            .userEntityUserId(userId)
            .label(credential.getLabel())
            .credentialType(PublicKeyCredentialType.valueOf(credential.getCredentialType().label()))
            .credentialId(Bytes.fromBase64(credential.getCredentialId()))
            .publicKey(credential.getPublicKeyCose())
            .signatureCount(credential.getSignatureCount())
            .uvInitialized(credential.getUvInitialized())
            .transports(credential.getTransports())
            .backupEligible(credential.getBackupEligible())
            .backupState(credential.getBackupState())
            .attestationObject(credential.getAttestationObject())
            .lastUsed(credential.getLastUsed())
            .created(credential.getCreatedDate())
            .build();
        // @formatter:on
    }

    private static PasskeyCredential toPasskeyCredential(PasskeyCredential credential, CredentialRecord credentialRecord, User user) {
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

    private static PasskeyCredential toPasskeyCredential(CredentialRecord credentialRecord, User user) {
        return toPasskeyCredential(new PasskeyCredential(), credentialRecord, user);
    }
}
