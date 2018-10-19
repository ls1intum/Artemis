package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ApollonDiagram;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the ApollonDiagram entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ApollonDiagramRepository extends JpaRepository<ApollonDiagram, Long> {

}
