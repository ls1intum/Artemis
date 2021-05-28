package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.modeling.ModelCluster;

/**
 * Spring Data JPA repository for the ModelCluster entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ModelClusterRepository extends JpaRepository<ModelCluster, Long> {

}
