package de.tum.cit.aet.artemis.atlas.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATLAS;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the ScienceEvent entity.
 */
@Profile(PROFILE_ATLAS)
@Repository
public interface ScienceEventRepository extends ArtemisJpaRepository<ScienceEvent, Long> {

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ScienceEvent se
            SET se.identity = :newIdentity
            WHERE se.identity = :oldIdentity
            """)
    void renameIdentity(@Param("oldIdentity") String oldIdentity, @Param("newIdentity") String newIdentity);

    Set<ScienceEvent> findAllByIdentity(String identity);
}
