package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.modeling.ApollonDiagram;

/**
 * Spring Data JPA repository for the ApollonDiagram entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ApollonDiagramRepository extends JpaRepository<ApollonDiagram, Long> {

    List<ApollonDiagram> findDiagramsByCourseId(Long courseId);
}
