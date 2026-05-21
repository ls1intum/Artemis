package de.tum.cit.aet.artemis.lecture.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

/**
 * Spring Data JPA repository for the Text Unit entity.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface TextUnitRepository extends ArtemisJpaRepository<TextUnit, Long> {

    @Query("""
            SELECT tu
            FROM TextUnit tu
                LEFT JOIN FETCH tu.competencyLinks cl
                LEFT JOIN FETCH cl.competency
            WHERE tu.id = :textUnitId
            """)
    Optional<TextUnit> findByIdWithCompetencies(@Param("textUnitId") Long textUnitId);
}
