package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.Result;

import org.springframework.data.jpa.repository.*;

import java.util.List;

/**
 * Spring Data JPA repository for the Result entity.
 */
@SuppressWarnings("unused")
public interface ResultRepository extends JpaRepository<Result,Long> {

    List<Result> findByParticipationId(Long participationId);
}
