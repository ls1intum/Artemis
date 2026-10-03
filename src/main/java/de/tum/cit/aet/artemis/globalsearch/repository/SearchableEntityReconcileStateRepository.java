package de.tum.cit.aet.artemis.globalsearch.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntityReconcileState;

/**
 * Spring Data JPA repository for {@link SearchableEntityReconcileState}, holding one row per reconcile pass.
 */
@Conditional(WeaviateEnabled.class)
@Lazy
@Repository
public interface SearchableEntityReconcileStateRepository extends ArtemisJpaRepository<SearchableEntityReconcileState, Long> {

    /**
     * Finds where a pass left off. Absent the first time a pass ever runs, and after the row has been cleared.
     *
     * @param pass the reconcile pass
     * @return the persisted position and current-cycle counters, if the pass has run before
     */
    Optional<SearchableEntityReconcileState> findByPass(ReconcilePass pass);
}
