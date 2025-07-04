package de.tum.cit.aet.artemis.core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.config.PasskeyEnabled;
import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Conditional(PasskeyEnabled.class)
@Lazy
@Repository
public interface PasskeyCredentialsRepository extends ArtemisJpaRepository<PasskeyCredential, String> {

    Optional<PasskeyCredential> findByCredentialId(String credentialId);

    @Query("""
            SELECT credential
            FROM PasskeyCredential credential
            WHERE credential.user.id = :userId
            """)
    List<PasskeyCredential> findByUser(@Param("userId") long userId);

    @Query("""
            SELECT COUNT(credential) > 0
            FROM PasskeyCredential credential
            WHERE credential.user.id = :userId
            """)
    boolean existsByUserId(@Param("userId") long userId);
}
