package de.tum.cit.aet.artemis.account.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.config.PasskeyEnabled;
import de.tum.cit.aet.artemis.account.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.account.dto.PasskeyAdminDTO;
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

    /**
     * Deletes all passkeys of a user, so that an authenticator cannot outlive the password it was enrolled alongside.
     *
     * @param userId the user whose passkeys are deleted
     * @return how many passkeys were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM PasskeyCredential credential
            WHERE credential.user.id = :userId
            """)
    int deleteAllByUserId(@Param("userId") long userId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.account.dto.PasskeyAdminDTO(
                pc.credentialId,
                pc.label,
                pc.createdDate,
                pc.lastUsed,
                pc.isSuperAdminApproved,
                u.id,
                u.login,
                CONCAT(u.firstName, ' ', u.lastName)
            )
            FROM PasskeyCredential pc
            JOIN pc.user u
            WHERE :#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF u.authorities
            """)
    List<PasskeyAdminDTO> findPasskeysForAdminUsers();
}
