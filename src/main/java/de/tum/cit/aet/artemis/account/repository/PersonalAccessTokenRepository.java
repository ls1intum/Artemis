package de.tum.cit.aet.artemis.account.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.PersonalAccessToken;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PersonalAccessTokenRepository extends ArtemisJpaRepository<PersonalAccessToken, Long> {

    Optional<PersonalAccessToken> findByTokenHash(String tokenHash);

    List<PersonalAccessToken> findAllByUserIdAndRevokedDateIsNullOrderByCreatedDateDesc(long userId);

    @Query("""
            SELECT COUNT(token)
            FROM PersonalAccessToken token
            WHERE token.userId = :userId
                AND token.revokedDate IS NULL
                AND token.expiryDate > :now
            """)
    long countUsableByUserId(@Param("userId") long userId, @Param("now") Instant now);

    /**
     * Revokes one token of a user. Returns the number of tokens revoked, so that a token that does not exist, belongs to
     * somebody else or is already revoked is told apart from a success without a separate read.
     *
     * @param id     the token id
     * @param userId the owner, so that nobody can revoke another user's token
     * @param now    the revocation time
     * @return 1 if the token was revoked, 0 otherwise
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE PersonalAccessToken token
            SET token.revokedDate = :now
            WHERE token.id = :id
                AND token.userId = :userId
                AND token.revokedDate IS NULL
            """)
    int revoke(@Param("id") long id, @Param("userId") long userId, @Param("now") Instant now);

    /**
     * Records that a token was used, at most once per interval, so that the list of tokens can show when each was last
     * used without a database write on every request.
     *
     * @param id        the token id
     * @param now       the usage time
     * @param threshold only tokens last used before this time (or never) are updated
     * @return the number of rows updated
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE PersonalAccessToken token
            SET token.lastUsedDate = :now
            WHERE token.id = :id
                AND (token.lastUsedDate IS NULL OR token.lastUsedDate < :threshold)
            """)
    int touchLastUsed(@Param("id") long id, @Param("now") Instant now, @Param("threshold") Instant threshold);
}
