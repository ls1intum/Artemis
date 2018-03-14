package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.QuestionStatistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the QuestionStatistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuestionStatisticRepository extends JpaRepository<QuestionStatistic,Long> {

}
