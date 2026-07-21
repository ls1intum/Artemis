package de.tum.cit.aet.artemis.modeling.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ApollonDiagram;

/**
 * Spring Data JPA repository for the ApollonDiagram entity.
 */
@Conditional(ModelingEnabled.class)
@Lazy
@Repository
public interface ApollonDiagramRepository extends ArtemisJpaRepository<ApollonDiagram, Long> {

    List<ApollonDiagram> findDiagramsByCourseId(Long courseId);

    Optional<ApollonDiagram> findByIdAndCourseId(long apollonDiagramId, long courseId);

    /**
     * Find an apollon diagram by id, scoped to a specific course. Use this in course-scoped endpoints to ensure
     * a caller authorized for one course cannot read, overwrite, or delete a diagram that belongs to a different
     * course, even if they pass that other course's diagram id under this course's path.
     *
     * @param apollonDiagramId the id of the apollon diagram
     * @param courseId         the id of the course the diagram must belong to
     * @return the apollon diagram
     * @throws de.tum.cit.aet.artemis.core.exception.EntityNotFoundException if no diagram with the given id exists in the course
     */
    default ApollonDiagram findByIdAndCourseIdElseThrow(long apollonDiagramId, long courseId) {
        return getValueElseThrow(findByIdAndCourseId(apollonDiagramId, courseId), apollonDiagramId);
    }

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
