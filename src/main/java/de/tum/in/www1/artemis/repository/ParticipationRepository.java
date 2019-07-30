package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Participation;

@Repository
public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    @Query("select distinct pe from Participation pe left join fetch pe.submissions where pe.id = :#{#participationId}")
    Participation getOneWithEagerSubmissions(@Param("participationId") Long participationId);
}
