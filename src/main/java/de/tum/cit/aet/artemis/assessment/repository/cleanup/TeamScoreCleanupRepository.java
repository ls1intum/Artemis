package de.tum.cit.aet.artemis.assessment.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.TeamScore;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for cleaning up old and orphaned team score entries.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Repository
public interface TeamScoreCleanupRepository extends ArtemisJpaRepository<TeamScore, Long> {

    /**
     * Deletes {@link TeamScore} entries where the associated team is {@code null}.
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM TeamScore ps WHERE ps.team IS NULL
            """)
    void deleteOrphanTeamScore();
}
