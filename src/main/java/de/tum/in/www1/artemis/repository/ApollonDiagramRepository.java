package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

import de.tum.in.www1.artemis.domain.modeling.ApollonDiagram;

/**
 * Spring Data JPA repository for the ApollonDiagram entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ApollonDiagramRepository extends JpaRepository<ApollonDiagram, Long> {

    @Query("SELECT a FROM ApollonDiagram a WHERE a.courseId = :#{#courseId} OR a.courseId is null")
    List<ApollonDiagram> findDiagramsByCourse(Long courseId);
}
