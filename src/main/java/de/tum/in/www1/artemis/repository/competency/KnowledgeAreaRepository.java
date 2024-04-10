package de.tum.in.www1.artemis.repository.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the {@link KnowledgeArea} entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface KnowledgeAreaRepository extends JpaRepository<KnowledgeArea, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "children", "competencies" })
    Optional<KnowledgeArea> findWithChildrenAndCompetenciesById(long knowledgeAreaId);

    @EntityGraph(type = LOAD, attributePaths = "competencies")
    List<KnowledgeArea> findAllWithCompetenciesByOrderByTitleAsc();

    @NotNull
    default KnowledgeArea findWithChildrenAndCompetenciesByIdElseThrow(long knowledgeAreaId) throws EntityNotFoundException {
        return findWithChildrenAndCompetenciesById(knowledgeAreaId).orElseThrow(() -> new EntityNotFoundException("KnowledgeArea", knowledgeAreaId));
    }

    @NotNull
    default KnowledgeArea findByIdElseThrow(long knowledgeAreaId) throws EntityNotFoundException {
        return findById(knowledgeAreaId).orElseThrow(() -> new EntityNotFoundException("KnowledgeArea", knowledgeAreaId));
    }
}
