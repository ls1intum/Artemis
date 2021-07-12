package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.FeedbackConflict;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * Spring Data JPA repository for the FeedbackConflict entity.
 */
@Repository
public interface FeedbackConflictRepository extends JpaRepository<FeedbackConflict, Long> {

    @Query("select distinct conflict from FeedbackConflict conflict "
            + "left join fetch conflict.firstFeedback f1 left join fetch f1.result r1 left join fetch r1.submission left join fetch r1.feedbacks left join fetch r1.assessor "
            + "left join fetch conflict.secondFeedback f2 left join fetch f2.result r2 left join fetch r2.submission left join fetch r2.feedbacks left join fetch r2.assessor "
            + "where conflict.conflict = true and (conflict.firstFeedback.id = :feedbackId or conflict.secondFeedback.id = :feedbackId)")
    List<FeedbackConflict> findAllWithEagerFeedbackResultAndSubmissionByFeedbackId(@Param("feedbackId") Long feedbackId);

    @Query("select distinct conflict from FeedbackConflict conflict where conflict.conflict = true and (conflict.firstFeedback.id in (:feedbackIds) or conflict.secondFeedback.id in (:feedbackIds))")
    List<FeedbackConflict> findAllConflictsByFeedbackList(@Param("feedbackIds") List<Long> feedbackIds);

    List<FeedbackConflict> findByFirstFeedbackIdAndConflict(Long id, Boolean conflict);

    List<FeedbackConflict> findBySecondFeedbackIdAndConflict(Long id, Boolean conflict);

    @Query("select distinct conflict from FeedbackConflict conflict where (conflict.conflict = true or conflict.discard = true) and "
            + "((conflict.firstFeedback.id = :firstFeedbackId and conflict.secondFeedback.id = :secondFeedbackId) or "
            + "(conflict.secondFeedback.id = :firstFeedbackId and conflict.firstFeedback.id = :secondFeedbackId))")
    List<FeedbackConflict> findConflictsOrDiscardedOnesByFirstAndSecondFeedback(@Param("firstFeedbackId") Long firstFeedbackId, @Param("secondFeedbackId") Long secondFeedbackId);

    @Query("select distinct conflict from FeedbackConflict conflict " + "left join fetch conflict.firstFeedback f1 left join fetch f1.result r1 left join fetch r1.assessor "
            + "left join fetch conflict.secondFeedback f2 left join fetch f2.result r2 left join fetch r2.assessor " + "where conflict.id = :feedbackConflictId")
    Optional<FeedbackConflict> findByFeedbackConflictId(@Param("feedbackConflictId") Long feedbackConflictId);

    @NotNull
    default FeedbackConflict findByFeedbackConflictIdElseThrow(long feedbackConflictId) {
        return findByFeedbackConflictId(feedbackConflictId)
                .orElseThrow(() -> new BadRequestAlertException("No FeedbackConflict found for given feedbackConflictId.", "feedbackConflict", "feedbackConflictNotFound"));
    }
}
