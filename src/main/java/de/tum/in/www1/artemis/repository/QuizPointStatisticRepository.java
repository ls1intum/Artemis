package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizPointStatistic;

/**
 * Spring Data JPA repository for the QuizPointStatistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizPointStatisticRepository extends JpaRepository<QuizPointStatistic, Long> {

    @Query("""
            SELECT s
            FROM QuizPointStatistic s
                LEFT JOIN FETCH s.pointCounters
            WHERE s.id = :statisticId
            """)
    QuizPointStatistic findByIdWithPointCounters(@Param("statisticId") Long statisticId);
}
