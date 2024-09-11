package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;

/**
 * Spring Data JPA repository for the DragItem entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface DragItemRepository extends ArtemisJpaRepository<DragItem, Long> {

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = "question")
    Optional<DragItem> findWithEagerQuestionById(Long id);

    default DragItem findWithEagerQuestionByIdElseThrow(Long dragItemId) {
        return getValueElseThrow(findWithEagerQuestionById(dragItemId), dragItemId);
    }
}
