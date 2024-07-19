package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the Text Unit entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface TextUnitRepository extends ArtemisJpaRepository<TextUnit, Long> {

    @Query("""
            SELECT tu
            FROM TextUnit tu
                LEFT JOIN FETCH tu.competencies
            WHERE tu.id = :textUnitId
            """)
    Optional<TextUnit> findByIdWithCompetencies(@Param("textUnitId") Long textUnitId);

}
