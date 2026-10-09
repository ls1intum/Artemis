package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

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

    /**
     * Updates a presentation assessment if its version still matches the expected version.
     *
     * @param assessmentId    the presentation assessment id
     * @param courseId        the owning course id
     * @param expectedVersion the version read before validating the update
     * @param title           the updated title
     * @param description     the updated description
     * @param maxPoints       the updated maximum points
     * @param exercise        the linked exercise, or null for a standalone presentation
     * @return 1 if updated, or 0 if the assessment is absent or its version no longer matches
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE PresentationAssessment assessment
            SET assessment.title = :title,
                assessment.description = :description,
                assessment.maxPoints = :maxPoints,
                assessment.exercise = :exercise,
                assessment.version = assessment.version + 1
            WHERE assessment.id = :assessmentId
                AND assessment.course.id = :courseId
                AND assessment.version = :expectedVersion
            """)
    int updateIfVersionMatches(@Param("assessmentId") long assessmentId, @Param("courseId") long courseId, @Param("expectedVersion") long expectedVersion,
            @Param("title") String title, @Param("description") String description, @Param("maxPoints") double maxPoints, @Param("exercise") Exercise exercise);

    /**
     * Increments the presentation assessment version if it still matches the expected version.
     *
     * @param assessmentId    the presentation assessment id
     * @param courseId        the owning course id
     * @param expectedVersion the version read before validating the write
     * @return 1 if updated, or 0 if the assessment is absent or its version no longer matches
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE PresentationAssessment assessment
            SET assessment.version = assessment.version + 1
            WHERE assessment.id = :assessmentId
                AND assessment.course.id = :courseId
                AND assessment.version = :expectedVersion
            """)
    int incrementVersionIfMatches(@Param("assessmentId") long assessmentId, @Param("courseId") long courseId, @Param("expectedVersion") long expectedVersion);

}
