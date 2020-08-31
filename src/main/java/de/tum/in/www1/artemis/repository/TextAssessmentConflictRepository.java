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
            + "left join fetch conflict.firstFeedback f1 left join fetch f1.result r1 left join fetch r1.submission "
            + "left join fetch conflict.secondFeedback f2 left join fetch f2.result r2 left join fetch r2.submission "
            + "where conflict.firstFeedback.id in (:feedbackIds) or conflict.secondFeedback.id in (:feedbackIds)")
    List<TextAssessmentConflict> findAllByFeedback(@Param("feedbackIds") List<Long> feedbackIds);
}
