package de.tum.cit.aet.artemis.atlas.repository;

import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@ConditionalOnProperty(name = "artemis.atlas.enabled", havingValue = "true")
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
