package de.tum.cit.aet.artemis.account.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Deletes passkey credentials regardless of whether the passkey feature is enabled.
 * <p>
 * {@link PasskeyCredentialsRepository} is {@code @Conditional(PasskeyEnabled.class)}, so it is absent when passkeys are
 * disabled. Credential revocation must not depend on that flag: passkeys enrolled while the feature was on remain in the
 * database after it is turned off, and they become usable again the moment it is turned back on. A password reset
 * performed in between would otherwise report success while silently leaving those authenticators in place - which is
 * exactly the ordering an incident response follows (disable the feature, reset the affected passwords, re-enable).
 * <p>
 * Deliberately narrow: this exists only for the cleanup path, so it carries the delete and nothing else. Everything that
 * reads or writes passkeys still goes through {@link PasskeyCredentialsRepository} and stays behind the feature flag,
 * because {@link PasskeyCredential} is a plain entity and is mapped whether or not the feature is on.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PasskeyCredentialCleanupRepository extends ArtemisJpaRepository<PasskeyCredential, String> {

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
}
