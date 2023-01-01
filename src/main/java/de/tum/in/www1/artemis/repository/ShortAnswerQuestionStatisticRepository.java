package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.ShortAnswerQuestionStatistic;

@SuppressWarnings("unused")
@Repository
public interface ShortAnswerQuestionStatisticRepository extends JpaRepository<ShortAnswerQuestionStatistic, Long> {

    @Query("""
            SELECT s
            FROM ShortAnswerQuestionStatistic s
                LEFT JOIN FETCH s.shortAnswerSpotCounters
            WHERE s.id = :statisticId
            """)
    ShortAnswerQuestionStatistic findByIdWithShortAnswerSpotCounters(@Param("statisticId") Long statisticId);
}
