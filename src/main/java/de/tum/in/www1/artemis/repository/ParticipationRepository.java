package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import javax.validation.constraints.NotNull;

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

    @Query("select distinct p from Participation p left join fetch p.submissions left join fetch p.results where p.id = :#{#participationId}")
    Participation getOneWithEagerSubmissionsAndResults(@Param("participationId") Long participationId);

    @Query("""
            SELECT p FROM Participation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            WHERE p.id = :participationId
                AND (s.id = (SELECT max(ps.id) FROM p.submissions ps
                    WHERE (ps.type <> 'ILLEGAL' or ps.type is null))
                OR s.id = NULL)
            """)
    Optional<Participation> findByIdWithLatestLegalSubmissionAndResult(@Param("participationId") Long participationId);

    @Query("""
            SELECT p FROM Participation p
            LEFT JOIN FETCH p.submissions s
            WHERE p.id = :#{#participationId}
                AND (s.type <> 'ILLEGAL' or s.type is null)
            """)
    Optional<Participation> findWithEagerLegalSubmissionsById(Long participationId);

    @NotNull
    default Participation findByIdWithLegalSubmissionsElseThrow(long participationId) {
        return findWithEagerLegalSubmissionsById(participationId).orElseThrow(() -> new EntityNotFoundException("Participation", participationId));
    }

    @NotNull
    default Participation findByIdElseThrow(long participationId) {
        return findById(participationId).orElseThrow(() -> new EntityNotFoundException("Participation", participationId));
    }
}
