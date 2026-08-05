package de.tum.cit.aet.artemis.atlas.repository;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the ScienceEvent entity.
 */
@Conditional(AtlasEnabled.class)
@Lazy
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

    @Query("""
            SELECT se
            FROM ScienceEvent se
            WHERE se.courseId IN :courseIds
                AND (:from IS NULL OR se.timestamp >= :from)
                AND (:to IS NULL OR se.timestamp <= :to)
                AND se.type IN :eventTypes
            ORDER BY se.timestamp ASC
            """)
    Page<ScienceEvent> findForResearchExport(@Param("courseIds") Set<Long> courseIds, @Param("from") java.time.ZonedDateTime from, @Param("to") java.time.ZonedDateTime to,
            @Param("eventTypes") Set<ScienceEventType> eventTypes, Pageable pageable);

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            DELETE FROM ScienceEvent se
            WHERE se.identity = :identity
                AND se.courseId = :courseId
                AND se.type NOT IN :retainedTypes
            """)
    void deleteInteractionEventsByIdentityAndCourseId(@Param("identity") String identity, @Param("courseId") long courseId,
            @Param("retainedTypes") Set<ScienceEventType> retainedTypes);
}
