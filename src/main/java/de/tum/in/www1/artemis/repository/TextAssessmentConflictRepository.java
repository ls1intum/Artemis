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

    @Query("select distinct conflict from TextAssessmentConflict conflict "
            + "left join fetch conflict.firstFeedback f1 left join fetch f1.result r1 left join fetch r1.submission left join fetch r1.feedbacks left join fetch r1.assessor "
            + "left join fetch conflict.secondFeedback f2 left join fetch f2.result r2 left join fetch r2.submission left join fetch r2.feedbacks left join fetch r2.assessor "
            + "where conflict.conflict = true and (conflict.firstFeedback.id = :feedbackId or conflict.secondFeedback.id = :feedbackId)")
    List<TextAssessmentConflict> findAllByFeedback(@Param("feedbackId") Long feedbackId);

    @Query("select distinct conflict from TextAssessmentConflict conflict where conflict.conflict = true and (conflict.firstFeedback.id in (:feedbackIds) or conflict.secondFeedback.id in (:feedbackIds))")
    List<TextAssessmentConflict> findAllStoredConflictsByFeedbackList(@Param("feedbackIds") List<Long> feedbackIds);

    List<TextAssessmentConflict> findByFirstFeedbackIdAndConflict(Long id, Boolean conflict);

    List<TextAssessmentConflict> findBySecondFeedbackIdAndConflict(Long id, Boolean conflict);

    @Query("select distinct conflict from TextAssessmentConflict conflict where (conflict.conflict = true or conflict.markedAsNotConflict = true) and "
            + "((conflict.firstFeedback.id = :firstFeedbackId and conflict.secondFeedback.id = :secondFeedbackId) or "
            + "(conflict.secondFeedback.id = :firstFeedbackId and conflict.firstFeedback.id = :secondFeedbackId))")
    List<TextAssessmentConflict> findConflictsOrMarkedOnesByFirstAndSecondFeedback(@Param("firstFeedbackId") Long firstFeedbackId,
            @Param("secondFeedbackId") Long secondFeedbackId);

}
