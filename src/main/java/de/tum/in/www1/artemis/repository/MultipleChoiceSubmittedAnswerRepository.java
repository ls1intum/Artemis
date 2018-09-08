package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.MultipleChoiceSubmittedAnswer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data  repository for the MultipleChoiceSubmittedAnswer entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MultipleChoiceSubmittedAnswerRepository extends JpaRepository<MultipleChoiceSubmittedAnswer, Long> {

    @Query(value = "select distinct mc_submitted_answer from MultipleChoiceSubmittedAnswer mc_submitted_answer left join fetch mc_submitted_answer.selectedOptions",
        countQuery = "select count(distinct mc_submitted_answer) from MultipleChoiceSubmittedAnswer mc_submitted_answer")
    Page<MultipleChoiceSubmittedAnswer> findAllWithEagerRelationships(Pageable pageable);

    @Query(value = "select distinct mc_submitted_answer from MultipleChoiceSubmittedAnswer mc_submitted_answer left join fetch mc_submitted_answer.selectedOptions")
    List<MultipleChoiceSubmittedAnswer> findAllWithEagerRelationships();

    @Query("select mc_submitted_answer from MultipleChoiceSubmittedAnswer mc_submitted_answer left join fetch mc_submitted_answer.selectedOptions where mc_submitted_answer.id =:id")
    Optional<MultipleChoiceSubmittedAnswer> findOneWithEagerRelationships(@Param("id") Long id);

}
