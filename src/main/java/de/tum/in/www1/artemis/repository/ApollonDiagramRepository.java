package de.tum.in.www1.artemis.repository;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.modeling.ApollonDiagram;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the ApollonDiagram entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ApollonDiagramRepository extends JpaRepository<ApollonDiagram, Long> {

    List<ApollonDiagram> findDiagramsByCourseId(Long courseId);

    /**
     * Returns the title of the diagram with the given id.
     *
     * @param diagramId the id of the diagram
     * @return the name/title of the diagram or null if the diagram does not exist
     */
    @Query("""
            SELECT ad.title
            FROM ApollonDiagram ad
            WHERE ad.id = :diagramId
            """)
    @Cacheable(cacheNames = "diagramTitle", key = "#diagramId", unless = "#result == null")
    String getDiagramTitle(@Param("diagramId") Long diagramId);

    @NotNull
    default ApollonDiagram findByIdElseThrow(Long apollonDiagramId) throws EntityNotFoundException {
        return findById(apollonDiagramId).orElseThrow(() -> new EntityNotFoundException("ApollonDiagram", apollonDiagramId));
    }
}
