package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestionStatistic;

@SuppressWarnings("unused")
@Repository
public interface DragAndDropQuestionStatisticRepository extends JpaRepository<DragAndDropQuestionStatistic, Long> {

    @Query("""
            SELECT s
            FROM DragAndDropQuestionStatistic s
                LEFT JOIN FETCH s.dropLocationCounters
            WHERE s.id = :statisticId
            """)
    DragAndDropQuestionStatistic findByIdWithDropLocationCounters(@Param("statisticId") Long statisticId);
}
