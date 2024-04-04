package de.tum.in.www1.artemis.repository.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the {@link KnowledgeArea} entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface KnowledgeAreaRepository extends JpaRepository<KnowledgeArea, Long> {

    @NotNull
    default KnowledgeArea findByIdElseThrow(long knowledgeAreaId) throws EntityNotFoundException {
        return findById(knowledgeAreaId).orElseThrow(() -> new EntityNotFoundException("KnowledgeArea", knowledgeAreaId));
    }

    @Query("""
            SELECT knowledgeArea
            FROM KnowledgeArea knowledgeArea
                LEFT JOIN FETCH knowledgeArea.children
                LEFT JOIN FETCH knowledgeArea.competencies
            WHERE knowledgeArea.id = :knowledgeAreaId
            """)
    Optional<KnowledgeArea> findByIdWithChildrenAndCompetencies(@Param("knowledgeAreaId") long knowledgeAreaId);

    @NotNull
    default KnowledgeArea findByIdWithChildrenAndCompetenciesElseThrow(long knowledgeAreaId) throws EntityNotFoundException {
        return findByIdWithChildrenAndCompetencies(knowledgeAreaId).orElseThrow(() -> new EntityNotFoundException("KnowledgeArea", knowledgeAreaId));
    }
}
