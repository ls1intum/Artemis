package de.tum.cit.aet.artemis.globalsearch.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntitySyncState;

/**
 * Spring Data JPA repository for {@link SearchableEntitySyncState}, the ledger of confirmed Weaviate writes.
 * <p>
 * The dispatcher upserts a row here on every successful metadata write. A later reconcile pass reads it to
 * detect never-indexed or drifted entities.
 */
@Conditional(WeaviateEnabled.class)
@Lazy
@Repository
public interface SearchableEntitySyncStateRepository extends ArtemisJpaRepository<SearchableEntitySyncState, Long> {

    /**
     * Finds the ledger row for a single entity.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId   the database id of the entity
     * @return the ledger row if one exists
     */
    Optional<SearchableEntitySyncState> findByEntityTypeAndEntityId(String entityType, Long entityId);

    /**
     * Deletes the ledger row for a single entity, if present. Called when an entity is deleted so the ledger
     * stays truthful (a later reconcile pass must not treat a deleted entity as still synced). A single
     * modifying statement rather than a derived delete: the dispatcher calls this outside any transaction, and a
     * derived delete loads and removes the entity, which needs one.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId   the database id of the entity
     */
    @Transactional // ok because of the modifying delete
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM SearchableEntitySyncState s WHERE s.entityType = :entityType AND s.entityId = :entityId")
    void deleteByEntityTypeAndEntityId(@Param("entityType") String entityType, @Param("entityId") Long entityId);
}
