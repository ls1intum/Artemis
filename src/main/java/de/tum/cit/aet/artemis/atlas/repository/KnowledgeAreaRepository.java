package de.tum.cit.aet.artemis.atlas.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.KnowledgeArea;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the {@link KnowledgeArea} entity.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface KnowledgeAreaRepository extends ArtemisJpaRepository<KnowledgeArea, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "children", "competencies" })
    Optional<KnowledgeArea> findWithChildrenAndCompetenciesById(long knowledgeAreaId);

    @EntityGraph(type = LOAD, attributePaths = "competencies")
    List<KnowledgeArea> findAllWithCompetenciesByOrderByTitleAsc();

    // this method is needed as native MySQL queries do not get automatically cast to boolean
    default boolean isDescendantOf(long descendantId, long parentId) {
        return isDescendantOfAsLong(descendantId, parentId) > 0;
    }

    @Query(value = """
            WITH RECURSIVE transitive_closure(id) AS
            (
                (SELECT knowledge_area.id FROM knowledge_area WHERE knowledge_area.id = :parentId)
                UNION
                (
                    SELECT ka.id
                    FROM knowledge_area AS ka
                    JOIN transitive_closure AS tc ON ka.parent_id = tc.id
                )
            )
            SELECT COUNT(*) FROM transitive_closure WHERE transitive_closure.id = :descendantId
            """, nativeQuery = true)
    long isDescendantOfAsLong(@Param("descendantId") long descendantId, @Param("parentId") long parentId);
}
