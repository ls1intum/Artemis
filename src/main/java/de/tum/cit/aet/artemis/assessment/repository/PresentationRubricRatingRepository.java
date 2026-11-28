package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricRating;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricRatingExportDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricRatingLevelDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the PresentationRubricRating entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PresentationRubricRatingRepository extends ArtemisJpaRepository<PresentationRubricRating, Long> {

    List<PresentationRubricRating> findAllByInstanceIdOrderByCriterionPosition(long instanceId);

    /**
     * Returns the selected level position of every rating of a presentation assessment, to recalculate its scores.
     *
     * @param assessmentId the presentation assessment id
     * @return one entry per rating
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.PresentationRubricRatingLevelDTO(rating.instance.id, rating.criterion.id, rating.level.position)
            FROM PresentationRubricRating rating
            WHERE rating.instance.presentationAssessment.id = :assessmentId
            """)
    List<PresentationRubricRatingLevelDTO> findLevelPositionsByPresentationAssessmentId(@Param("assessmentId") long assessmentId);

    /**
     * Deletes the ratings of an instance before its new ratings are saved.
     *
     * @param instanceId the instance id
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM PresentationRubricRating rating
            WHERE rating.instance.id = :instanceId
            """)
    void deleteAllByInstanceId(@Param("instanceId") long instanceId);

    /**
     * Loads all rubric ratings of a student for the personal data export.
     *
     * @param userId the id of the student
     * @return the ratings ordered by course, presentation, date and criterion
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.PresentationRubricRatingExportDTO(
                course.title, assessment.title, instance.presentationDate, criterion.name, criterion.weight, criterion.scale, level.position, rating.comment)
            FROM PresentationRubricRating rating
            JOIN rating.instance instance
            JOIN instance.presentationAssessment assessment
            JOIN assessment.course course
            JOIN rating.criterion criterion
            JOIN rating.level level
            WHERE instance.student.id = :userId
            ORDER BY course.title, assessment.title, instance.presentationDate, instance.id, criterion.position
            """)
    List<PresentationRubricRatingExportDTO> findExportRowsByStudentId(@Param("userId") long userId);
}
