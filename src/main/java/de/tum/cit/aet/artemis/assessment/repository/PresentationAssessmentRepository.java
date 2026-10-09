package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the PresentationAssessment entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PresentationAssessmentRepository extends ArtemisJpaRepository<PresentationAssessment, Long> {

    /**
     * Returns presentation definitions ordered by title and id, without fetching instances.
     *
     * @param courseId the owning course id
     * @return the presentation definitions for the course
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentDTO(
                assessment.id, assessment.title, assessment.description, assessment.maxPoints,
                assessment.course.id, exercise.id, exercise.title)
            FROM PresentationAssessment assessment
            LEFT JOIN assessment.exercise exercise
            WHERE assessment.course.id = :courseId
            ORDER BY assessment.title, assessment.id
            """)
    List<PresentationAssessmentDTO> findAllByCourseId(@Param("courseId") long courseId);

    Optional<PresentationAssessment> findOneByIdAndCourseId(long id, long courseId);

}
