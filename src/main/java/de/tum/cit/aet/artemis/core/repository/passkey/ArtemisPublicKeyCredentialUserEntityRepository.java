package de.tum.cit.aet.artemis.core.repository.passkey;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.config.PasskeyEnabled;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.converter.BytesConverter;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

/**
 * Repository implementation for managing {@link PublicKeyCredentialUserEntity} entities.
 * This class implements the {@link PublicKeyCredentialUserEntityRepository} interface, which will be called by
 * <a href="https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html">Spring Security WebAuthn</a> to persist passkeys.
 * <p>
 * In our case the user UserEntity is {@link User}, but we cannot directly implement the interface there as {@link de.tum.cit.aet.artemis.exercise.domain.participation.Participant}
 * is implemented by {@link User} and the declarations for the method {@link de.tum.cit.aet.artemis.exercise.domain.participation.Participant#getId} and
 * {@link PublicKeyCredentialUserEntity#getId()} clash.
 * </p>
 * <p>
 * Note: {@code save} and {@code delete} are not implemented and should use the {@link UserRepository} instead.
 * </p>
 *
 * @see PublicKeyCredentialUserEntityRepository
 * @see UserRepository
 * @see de.tum.cit.aet.artemis.core.security.passkey.ArtemisWebAuthnConfigurer
 */
@Conditional(PasskeyEnabled.class)
@Lazy
@Repository
public class ArtemisPublicKeyCredentialUserEntityRepository implements PublicKeyCredentialUserEntityRepository {

    private static final Logger log = LoggerFactory.getLogger(ArtemisPublicKeyCredentialUserEntityRepository.class);

    private final UserRepository userRepository;

    public ArtemisPublicKeyCredentialUserEntityRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findArtemisUserById(Bytes id) {
        Long userId = BytesConverter.bytesToLong(id);
        return userRepository.findOneWithGroupsAndAuthoritiesById(userId);
    }

    @Override
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        return findArtemisUserById(id).map(ArtemisPublicKeyCredentialUserEntityRepository::mapToUserEntity).orElse(null);
    }

    /**
     * We are considering the login as username for implementing this interface
     */
    @Override
    public PublicKeyCredentialUserEntity findByUsername(String login) {
        return userRepository.findOneByLogin(login).map(ArtemisPublicKeyCredentialUserEntityRepository::mapToUserEntity).orElse(null);
    }

    /**
     * <p>
     * This method will be called by the SpringSecurity WebAuthn implementation on passkey login (on every authentication).
     * </p>
     * <p>
     * The user entity will not contain useful information, e.g.
     * <ul>
     * <li>id=FpaBAewJEbgnGd1U0cqdyUp94hfaboE9Ubir-k6ScTP</li>
     * <li>name=anonymousUser</li>
     * <li>displayName=anonymousUser</li>
     * </p>
     * <b>We therefore do not save the information in the database.<br>
     * </b>
     * In the context of WebAuthn and passkey user entity updates should not be required.
     * If you want to update the user entity, use the {@link UserRepository} instead.
     */
    @Override
    public void save(PublicKeyCredentialUserEntity userEntity) {
        log.debug("save not implemented in ArtemisPublicKeyCredentialUserEntityRepository, use UserRepository instead");
    }

    /**
     * It should not be required to delete the user entity in the context of webAuthn and passkey logins, therefore, this
     * repository does not implement this functionality. Use {@link UserRepository} instead if you want to delete a
     * user entity.
     */
    @Override
    public void delete(Bytes id) {
        log.warn("delete not implemented in ArtemisPublicKeyCredentialUserEntityRepository, use UserRepository instead");
    }

    private static PublicKeyCredentialUserEntity mapToUserEntity(User user) {
        return ImmutablePublicKeyCredentialUserEntity.builder().id(user.getExternalId()).name(user.getLogin()).displayName(user.getName()).build();
    }
}
