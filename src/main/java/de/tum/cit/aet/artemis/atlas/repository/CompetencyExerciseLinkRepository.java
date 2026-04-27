package de.tum.cit.aet.artemis.atlas.repository;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface CompetencyExerciseLinkRepository extends ArtemisJpaRepository<CompetencyExerciseLink, Long> {

    @Query("""
                SELECT cel FROM CompetencyExerciseLink cel
                LEFT JOIN FETCH cel.competency
                WHERE cel.exercise.id = :exerciseId
            """)
    List<CompetencyExerciseLink> findByExerciseIdWithCompetency(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT cel
            FROM CompetencyExerciseLink cel
            LEFT JOIN FETCH cel.competency
            WHERE cel.exercise.id IN :exerciseIds
            """)
    Set<CompetencyExerciseLink> findByExerciseIdInWithCompetency(@Param("exerciseIds") Set<Long> exerciseIds);

}
