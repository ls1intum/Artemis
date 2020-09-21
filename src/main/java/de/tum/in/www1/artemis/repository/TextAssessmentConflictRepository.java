package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextAssessmentConflict;

/**
 * Spring Data JPA repository for the TextAssessmentConflict entity.
 */
@Repository
public interface TextAssessmentConflictRepository extends JpaRepository<TextAssessmentConflict, Long> {

    @Query("select distinct conflict from TextAssessmentConflict conflict where conflict.conflict = true and (conflict.firstFeedback.id in (:feedbackIds) or conflict.secondFeedback.id in (:feedbackIds))")
    List<TextAssessmentConflict> findAllByFeedbackList(@Param("feedbackIds") List<Long> feedbackIds);

    @Query("select distinct conflict from TextAssessmentConflict conflict where conflict.conflict = true and "
            + "((conflict.firstFeedback.id = :firstFeedbackId and conflict.secondFeedback.id = :secondFeedbackId) or "
            + "(conflict.secondFeedback.id = :firstFeedbackId and conflict.firstFeedback.id = :secondFeedbackId))")
    List<TextAssessmentConflict> findByFirstAndSecondFeedback(@Param("firstFeedbackId") Long firstFeedbackId, @Param("secondFeedbackId") Long secondFeedbackId);

}
