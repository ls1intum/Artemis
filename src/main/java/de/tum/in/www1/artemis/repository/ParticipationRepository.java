package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.participation.Participation;

@Repository
public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    @Query("select distinct p from Participation p left join fetch p.submissions where p.id = :#{#participationId}")
    Participation getOneWithEagerSubmissions(@Param("participationId") Long participationId);

    @Query("select distinct p from Participation p left join fetch p.submissions left join fetch p.results where p.id = :#{#participationId}")
    Participation getOneWithEagerSubmissionsAndResults(@Param("participationId") Long participationId);

    @Query("select p from Participation p left join fetch p.results pr left join fetch pr.feedbacks prf where p.id = :participationId and (pr.id = (select max(id) from p.results) or pr.id = null)")
    Optional<Participation> findByIdWithLatestResultAndFeedbacks(@Param("participationId") Long participationId);

    /**
     * Find the participation with the given id. Additionally, load all the submissions from the database. Returns an empty Optional if the
     * participation could not be found.
     *
     * @param participationId the id of the participation
     * @return the participation with eager submissions or an empty Optional
     */
    @EntityGraph(attributePaths = { "submissions" })
    Optional<Participation> findWithEagerSubmissionsById(Long participationId);
}
