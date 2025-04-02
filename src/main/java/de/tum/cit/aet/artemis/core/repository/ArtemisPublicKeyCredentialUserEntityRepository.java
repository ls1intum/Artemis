package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.User;

@Profile(PROFILE_CORE)
@Repository
public class ArtemisPublicKeyCredentialUserEntityRepository implements PublicKeyCredentialUserEntityRepository {

    private static final Logger log = LoggerFactory.getLogger(ArtemisUserCredentialRepository.class);

    private final UserRepository userRepository;

    public ArtemisPublicKeyCredentialUserEntityRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findArtemisUserById(Bytes id) {
        Long userId = User.bytesToLong(id);
        return userRepository.findById(userId);
    }

    @Override
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        log.info("findById: id={}", id.toBase64UrlString());

        return findArtemisUserById(id).map(ArtemisPublicKeyCredentialUserEntityRepository::mapToUserEntity).orElse(null);
    }

    /**
     * We are considering the login as username for implementing this interface
     */
    @Override
    public PublicKeyCredentialUserEntity findByUsername(String login) {
        log.info("findByUsername: username={}", login);

        return userRepository.findOneByLogin(login).map(ArtemisPublicKeyCredentialUserEntityRepository::mapToUserEntity).orElse(null);
    }

    @Override
    public void save(PublicKeyCredentialUserEntity userEntity) {
        throw new NotImplementedException("save not implemented, use userRepository instead");
    }

    @Override
    public void delete(Bytes id) {
        throw new NotImplementedException("delete not implemented, use userRepository instead");
    }

    private static PublicKeyCredentialUserEntity mapToUserEntity(User user) {
        return ImmutablePublicKeyCredentialUserEntity.builder().id(user.getExternalId()).name(user.getLogin()).displayName(user.getName()).build();
    }

    // // TODO add comment
    // private static Bytes longToBytes(Long value) {
    // String userIdAsBase64 = Base64.getEncoder().encodeToString(value.toString().getBytes());
    // return Bytes.fromBase64(userIdAsBase64);
    // }
    //
    // // TODO add comment for usage (bas64 decoding)
    // private static Long bytesToLong(Bytes value) {
    // byte[] decodedBytes = Base64.getDecoder().decode(value.toBase64UrlString());
    // String decodedString = new String(decodedBytes);
    // return Long.parseLong(decodedString);
    // }
}
