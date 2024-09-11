package de.tum.cit.aet.artemis.repository.science;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.domain.science.ScienceEventType;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the ScienceEvent entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ScienceEventRepository extends ArtemisJpaRepository<ScienceEvent, Long> {

    Set<ScienceEvent> findAllByType(ScienceEventType type);

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
