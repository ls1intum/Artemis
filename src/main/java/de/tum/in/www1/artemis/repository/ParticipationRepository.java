package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.participation.Participation;

@Repository
public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    @Query("SELECT DISTINCT p FROM Participation p LEFT JOIN FETCH p.submissions LEFT JOIN FETCH p.results WHERE p.id = :#{#participationId}")
    Participation getOneWithEagerSubmissionsAndResults(@Param("participationId") Long participationId);

    @Query("SELECT p FROM Participation p LEFT JOIN FETCH p.results pr LEFT JOIN FETCH pr.feedbacks prf WHERE p.id = :participationId AND (pr.id = (SELECT MAX(id) FROM p.results) OR pr.id = NULL)")
    Optional<Participation> findWithLatestResultAndFeedbacksById(@Param("participationId") Long participationId);

    /**
     * Find the participation with the given id. Additionally, load all the submissions from the database. Returns an empty Optional if the
     * participation could not be found.
     *
     * @param participationId the id of the participation
     * @return the participation with eager submissions or an empty Optional
     */
    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<Participation> findWithEagerSubmissionsById(Long participationId);
}
