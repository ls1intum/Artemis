package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.modeling.ApollonDiagram;

/**
 * Spring Data JPA repository for the ApollonDiagram entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ApollonDiagramRepository extends JpaRepository<ApollonDiagram, Long> {

    List<ApollonDiagram> findDiagramsByCourseId(Long courseId);

    /**
     * Returns the title of the diagram with the given id
     *
     * @param diagramId the id of the diagram
     * @return the name/title of the diagram
     */
    @Query("""
            select ad.title
            from ApollonDiagram ad
            where ad.id = :diagramId
            """)
    String getDiagramTitle(Long diagramId);
}
