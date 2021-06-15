package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.modeling.ModelElement;

/**
 * Spring Data JPA repository for the ModelElement entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ModelElementRepository extends JpaRepository<ModelElement, Long> {

    @Query("select element from ModelElement element left join fetch element.cluster cluster where element.modelElementId = :#{#modelElementId}")
    ModelElement findByModelElementIdWithCluster(@Param("modelElementId") String elementId);

    List<ModelElement> findByModelElementIdIn(List<String> elementIds);
}
