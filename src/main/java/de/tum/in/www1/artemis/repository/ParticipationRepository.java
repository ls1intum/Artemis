package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.StudentParticipation;

@Repository
public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    @Query("select distinct p from Participation p left join fetch p.submissions where p.id = :#{#participationId}")
    Participation getOneWithEagerSubmissions(@Param("participationId") Long participationId);

    @EntityGraph(attributePaths = { "submissions", "results.submission" })
    @Query("select pe from Participation pe")
    List<Participation> getAllWithEagerSubmissionsAndResults();

    @Query("select distinct participation from Participation participation left join fetch participation.submissions submission left join fetch participation.results where participation.student.login = :#{#username}")
    List<StudentParticipation> findByStudentUsernameWithEagerSubmissionsWithEagerResults(@Param("username") String username);
}
