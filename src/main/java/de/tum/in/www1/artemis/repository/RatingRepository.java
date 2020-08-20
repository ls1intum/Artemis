package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Rating;

/**
 * Spring Data JPA repository for the Rating entity.
 */
@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findRatingByResultId(Long resultId);

    /**
     * Delete all ratings that belong to results of a given participation
     * @param participationId the Id of the participation where the ratings should be deleted
     */
    void deleteByResult_Participation_Id(Long participationId);

    /**
     * Delete all ratings that belong to the given result
     * @param resultId the Id of the result where the rating should be deleted
     */
    void deleteByResult_Id(long resultId);
}
