package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    Optional<Participation> findByResults(Result result);

    @Query("""
            SELECT DISTINCT p FROM Participation p
            LEFT JOIN FETCH p.submissions
            LEFT JOIN FETCH p.results
            WHERE p.id = :#{#participationId}
            """)
    Participation getOneWithEagerSubmissionsAndResults(@Param("participationId") Long participationId);

    @Query("""
            SELECT p FROM Participation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            WHERE p.id = :participationId
            AND (s.id = (
            		SELECT max(id) FROM p.submissions)
            OR s.id = NULL)
            """)
    Optional<Participation> findByIdWithLatestSubmissionAndResult(@Param("participationId") Long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<Participation> findWithEagerSubmissionsById(Long participationId);

    @NotNull
    default Participation findByIdWithSubmissionsElseThrow(long participationId) {
        return findWithEagerSubmissionsById(participationId).orElseThrow(() -> new EntityNotFoundException("Participation", participationId));
    }

    @NotNull
    default Participation findByIdElseThrow(long participationId) {
        return findById(participationId).orElseThrow(() -> new EntityNotFoundException("Participation", participationId));
    }
}
