package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.UserVCSAccessToken;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface UserVCSAccessTokenRepository extends ArtemisJpaRepository<UserVCSAccessToken, Long> {

    Optional<UserVCSAccessToken> findByUserId(long userId);

    /**
     * Finds the tokens expiring within the given window, for the notification that warns their owners.
     *
     * @param from start of the window
     * @param to   end of the window
     * @return the user ids whose token expires in that window
     */
    @Query("""
            SELECT token.userId
            FROM UserVCSAccessToken token
            WHERE token.expiryDate BETWEEN :from AND :to
            """)
    List<Long> findUserIdsWithTokenExpiringBetween(@Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            DELETE FROM UserVCSAccessToken token
            WHERE token.userId = :userId
            """)
    void deleteByUserId(@Param("userId") long userId);
}
