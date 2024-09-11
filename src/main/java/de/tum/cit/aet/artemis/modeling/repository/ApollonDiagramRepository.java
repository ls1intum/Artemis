package de.tum.cit.aet.artemis.modeling.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.modeling.domain.ApollonDiagram;

/**
 * Spring Data JPA repository for the ApollonDiagram entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ApollonDiagramRepository extends ArtemisJpaRepository<ApollonDiagram, Long> {

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
}
