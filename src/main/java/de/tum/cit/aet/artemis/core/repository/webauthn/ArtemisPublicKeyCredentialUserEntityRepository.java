package de.tum.cit.aet.artemis.core.repository.webauthn;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.converter.BytesConverter;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@Profile(PROFILE_CORE)
@Repository
public class ArtemisPublicKeyCredentialUserEntityRepository implements PublicKeyCredentialUserEntityRepository {

    private static final Logger log = LoggerFactory.getLogger(ArtemisPublicKeyCredentialUserEntityRepository.class);

    private final UserRepository userRepository;

    public ArtemisPublicKeyCredentialUserEntityRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findArtemisUserById(Bytes id) {
        Long userId = BytesConverter.bytesToLong(id);
        return userRepository.findById(userId);
    }

    @Override
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        log.debug("findById: id={}", id.toBase64UrlString());

        return findArtemisUserById(id).map(ArtemisPublicKeyCredentialUserEntityRepository::mapToUserEntity).orElse(null);
    }

    /**
     * We are considering the login as username for implementing this interface
     */
    @Override
    public PublicKeyCredentialUserEntity findByUsername(String login) {
        log.debug("findByUsername: username={}", login);

        return userRepository.findOneByLogin(login).map(ArtemisPublicKeyCredentialUserEntityRepository::mapToUserEntity).orElse(null);
    }

    @Override
    public void save(PublicKeyCredentialUserEntity userEntity) {
        log.warn("save not implemented in ArtemisPublicKeyCredentialUserEntityRepository, use UserRepository instead");
    }

    @Override
    public void delete(Bytes id) {
        log.warn("delete not implemented in ArtemisPublicKeyCredentialUserEntityRepository, use UserRepository instead");
    }

    private static PublicKeyCredentialUserEntity mapToUserEntity(User user) {
        return ImmutablePublicKeyCredentialUserEntity.builder().id(user.getExternalId()).name(user.getLogin()).displayName(user.getName()).build();
    }
}
