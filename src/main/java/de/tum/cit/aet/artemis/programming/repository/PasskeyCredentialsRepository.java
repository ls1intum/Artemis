package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.PasskeyCredentials;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface PasskeyCredentialsRepository extends ArtemisJpaRepository<PasskeyCredentials, String> {

    @Query("SELECT authenticator FROM PasskeyCredentials authenticator WHERE authenticator.attestedCredentialData.credentialId = :credentialId")
    Optional<PasskeyCredentials> findOneByCredentialId(@Param("credentialId") byte[] credentialId);

    // TODO fix email address vs user login vs user id
    @Query("SELECT authenticator FROM PasskeyCredentials authenticator WHERE authenticator.user.email = :emailAddress")
    List<PasskeyCredentials> findAllByEmailAddress(String emailAddress);

    List<PasskeyCredentials> findByUserId(@Param("userId") Long userId);
}
