package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface PasskeyCredentialsRepository extends ArtemisJpaRepository<PasskeyCredential, String> {

    Optional<PasskeyCredential> findByCredentialId(String credentialId);

    @Query("""
                SELECT credential
                FROM PasskeyCredential credential
                WHERE credential.user.id = :userId
            """)
    List<PasskeyCredential> findByUser(@Param("userId") long userId);
}
