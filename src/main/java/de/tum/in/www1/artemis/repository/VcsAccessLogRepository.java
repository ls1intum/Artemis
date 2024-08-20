package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.vcstokens.VcsAccessLog;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the User entity.<br>
 * <br>
 * <p>
 * <b>Note</b>: Please keep in mind that the User entities are soft-deleted when adding new queries to this repository.
 * If you don't need deleted user entities, add `WHERE user.isDeleted = FALSE` to your query.
 * </p>
 */
@Profile(PROFILE_CORE)
@Repository
public interface VcsAccessLogRepository extends ArtemisJpaRepository<VcsAccessLog, Long> {

    /**
     * Find the access log entry which does not have any commit hash yet
     *
     * @param participationId Current time
     * @return a log entry belonging with the participationId, which has no commit hash
     */
    @Query("""
            SELECT vcsAccessLog
            FROM VcsAccessLog vcsAccessLog
            WHERE vcsAccessLog.participation.id = :participationId
                AND vcsAccessLog.commitHash IS NULL
            """)
    Optional<VcsAccessLog> findByParticipationIdWhereCommitHashIsNull(@Param("participationId") long participationId);

}
