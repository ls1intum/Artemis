package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.TextUnit;

/**
 * Spring Data JPA repository for the Text Unit entity.
 */
@Repository
public interface TextUnitRepository extends JpaRepository<TextUnit, Long> {

    @Query("""
            SELECT tu FROM TextUnit tu
                LEFT JOIN FETCH tu.competencies
            WHERE tu.id = :textUnitId
            """)
    Optional<TextUnit> findByIdWithCompetencies(@Param("textUnitId") Long textUnitId);

    @Query("""
            SELECT tu FROM TextUnit tu
                LEFT JOIN FETCH tu.competencies c
                LEFT JOIN FETCH c.lectureUnits
            WHERE tu.id = :textUnitId
            """)
    Optional<TextUnit> findByIdWithCompetenciesBidirectional(@Param("textUnitId") Long textUnitId);

}
